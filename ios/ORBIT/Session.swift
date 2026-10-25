import Foundation
import Security
import Observation

/// The built-in ORBIT Brain is always the guide. Set this to true only if you also run the optional cloud guide on the backend.
enum Config { static let useCloudAI = false }

struct Consent: Codable, Equatable { var ai = false; var location = false; var leaderboard = false }
struct UserInfo: Codable, Equatable { var identifier: String; var displayName: String; var consent: Consent }

/// Minimal Keychain wrapper: the login token and profile never touch UserDefaults.
enum Keychain {
    private static func query(_ key: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.crowncouncil.orbit", kSecAttrAccount as String: key]
    }
    static func set(_ data: Data?, for key: String) {
        SecItemDelete(query(key) as CFDictionary)
        guard let data else { return }
        var add = query(key)
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }
    static func get(_ key: String) -> Data? {
        var q = query(key)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: AnyObject?
        return SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess ? out as? Data : nil
    }
}

struct ApiResult {
    let status: Int
    let json: [String: Any]?
    var ok: Bool { (200..<300).contains(status) }
    var error: String? { json?["error"] as? String }
    var message: String? { json?["message"] as? String }
}

/// Login state plus a small JSON-over-HTTP client for the ORBIT backend.
@Observable
final class AppSession {
    private(set) var token: String?
    private(set) var user: UserInfo?
    var guest: Bool

    /// Set ORBIT_API_URL in Info.plist. Plain http:// works only for local development (see README).
    let baseURL: String = (Bundle.main.object(forInfoDictionaryKey: "ORBIT_API_URL") as? String) ?? "http://localhost:8080"

    init() {
        token = Keychain.get("token").flatMap { String(data: $0, encoding: .utf8) }
        user = Keychain.get("user").flatMap { try? JSONDecoder().decode(UserInfo.self, from: $0) }
        guest = UserDefaults.standard.bool(forKey: "orbit.guest")
    }

    var signedIn: Bool { token != nil && user != nil }
    var needsAuth: Bool { !signedIn && !guest }

    func continueAsGuest() { guest = true; UserDefaults.standard.set(true, forKey: "orbit.guest") }

    func signOut() {
        token = nil; user = nil; guest = false
        Keychain.set(nil, for: "token"); Keychain.set(nil, for: "user")
        UserDefaults.standard.set(false, forKey: "orbit.guest")
    }

    private func store(token: String, user: UserInfo) {
        self.token = token; self.user = user; guest = false
        Keychain.set(token.data(using: .utf8), for: "token")
        Keychain.set(try? JSONEncoder().encode(user), for: "user")
        UserDefaults.standard.set(false, forKey: "orbit.guest")
    }

    func setUser(_ u: UserInfo) { user = u; Keychain.set(try? JSONEncoder().encode(u), for: "user") }

    func request(_ method: String, _ path: String, body: [String: Any]? = nil, auth: Bool = true) async -> ApiResult {
        guard let url = URL(string: baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + path) else { return ApiResult(status: 0, json: nil) }
        var req = URLRequest(url: url, timeoutInterval: 30)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if auth, let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try? JSONSerialization.data(withJSONObject: body)
        }
        do {
            let (data, resp) = try await URLSession.shared.data(for: req)
            let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
            return ApiResult(status: status, json: (try? JSONSerialization.jsonObject(with: data)) as? [String: Any])
        } catch {
            return ApiResult(status: 0, json: nil)
        }
    }

    // MARK: account operations
    func signup(identifier: String, password: String, name: String, consent: Consent) async -> ApiResult {
        await request("POST", "/api/auth/signup", body: [
            "identifier": identifier, "password": password, "displayName": name,
            "consent": ["terms": true, "privacy": true, "ai": consent.ai, "location": consent.location, "leaderboard": consent.leaderboard],
        ], auth: false)
    }

    func verify(identifier: String, code: String) async -> ApiResult {
        let r = await request("POST", "/api/auth/verify", body: ["identifier": identifier, "code": code], auth: false)
        finish(r); return r
    }

    func login(identifier: String, password: String) async -> ApiResult {
        let r = await request("POST", "/api/auth/login", body: ["identifier": identifier, "password": password], auth: false)
        finish(r); return r
    }

    private func finish(_ r: ApiResult) {
        guard r.ok, let t = r.json?["token"] as? String, let u = r.json?["user"] as? [String: Any] else { return }
        let c = u["consent"] as? [String: Any] ?? [:]
        store(token: t, user: UserInfo(identifier: u["identifier"] as? String ?? "", displayName: u["displayName"] as? String ?? "",
                                       consent: Consent(ai: c["ai"] as? Bool ?? false, location: c["location"] as? Bool ?? false, leaderboard: c["leaderboard"] as? Bool ?? false)))
    }

    func updateConsent(_ c: Consent) async -> ApiResult {
        let r = await request("PUT", "/api/me/consent", body: ["ai": c.ai, "location": c.location, "leaderboard": c.leaderboard])
        if r.ok, var u = user { u.consent = c; setUser(u) }
        return r
    }

    func deleteAccount() async -> ApiResult {
        let r = await request("DELETE", "/api/me")
        if r.ok { signOut() }
        return r
    }
}
