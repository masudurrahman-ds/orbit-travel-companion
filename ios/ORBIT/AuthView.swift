import SwiftUI

private enum AuthStep { case welcome, login, signup, verify }

private func friendly(_ r: ApiResult) -> String {
    if let m = r.message { return m }
    if r.status == 0 { return "Cannot reach the server. Check your internet connection and the server address." }
    switch r.error {
    case "invalid_credentials": return "Wrong email, number or password."
    case "not_verified": return "Verify your account first. Create the account again to get a new code."
    case "account_exists": return "An account with this email or number already exists. Try signing in."
    case "invalid_code": return "That code is wrong or expired."
    case "too_many_requests": return "Too many attempts. Wait a few minutes and try again."
    default: return "Something went wrong. Please try again."
    }
}

/// Shown on first launch until the traveller signs in, creates an account or continues as guest.
struct AuthGate: View {
    @Environment(AppSession.self) private var session
    @State private var step: AuthStep = .welcome
    @State private var identifier = ""
    @State private var devCode: String?

    var body: some View {
        ZStack {
            LinearGradient(colors: [Theme.navy, Color(red: 0.04, green: 0.12, blue: 0.23)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 14) {
                    Text("ORBIT").font(.system(size: 44, weight: .bold, design: .serif)).foregroundStyle(Theme.gold)
                    Text("Plan smarter. Travel better.").foregroundStyle(.white.opacity(0.85))
                    switch step {
                    case .welcome: welcome
                    case .login: LoginForm(back: { step = .welcome }, signup: { step = .signup })
                    case .signup: SignupForm(back: { step = .welcome }) { id, code in identifier = id; devCode = code; step = .verify }
                    case .verify: VerifyForm(identifier: identifier, devCode: devCode, back: { step = .welcome })
                    }
                }
                .frame(maxWidth: 480).padding(20)
            }
        }
    }

    private var welcome: some View {
        VStack(spacing: 12) {
            Button { step = .signup } label: { Text("Create account").frame(maxWidth: .infinity) }
                .buttonStyle(.borderedProminent).tint(Theme.gold).controlSize(.large)
            Button { step = .login } label: { Text("Sign in").frame(maxWidth: .infinity) }
                .buttonStyle(.bordered).controlSize(.large)
            Button("Continue as guest") { session.continueAsGuest() }
            Text("Guest mode keeps everything on this phone, including the built-in guide. Sync, rankings and spot submissions need an account.")
                .font(.footnote).foregroundStyle(.secondary)
        }
        .padding(20).background(Color(.systemBackground), in: RoundedRectangle(cornerRadius: 24))
    }
}

private struct Card<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 12, content: content)
            .padding(20).background(Color(.systemBackground), in: RoundedRectangle(cornerRadius: 24))
    }
}

private struct LoginForm: View {
    @Environment(AppSession.self) private var session
    let back: () -> Void
    let signup: () -> Void
    @State private var id = ""
    @State private var pw = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        Card {
            Text("Sign in").font(.title2.bold())
            TextField("Email or mobile number", text: $id).textInputAutocapitalization(.never).keyboardType(.emailAddress).textFieldStyle(.roundedBorder)
            SecureField("Password", text: $pw).textFieldStyle(.roundedBorder)
            if let error { Text(error).font(.footnote).foregroundStyle(Theme.rose) }
            Button {
                busy = true; error = nil
                Task { let r = await session.login(identifier: id.trimmingCharacters(in: .whitespaces), password: pw); busy = false; if !r.ok { error = friendly(r) } }
            } label: { Text(busy ? "Signing in..." : "Sign in").frame(maxWidth: .infinity) }
            .buttonStyle(.borderedProminent).tint(Theme.navy).disabled(busy || id.isEmpty || pw.isEmpty)
            HStack { Button("Back", action: back); Spacer(); Button("Create account", action: signup) }
        }
    }
}

private struct SignupForm: View {
    @Environment(AppSession.self) private var session
    let back: () -> Void
    let created: (String, String?) -> Void
    @State private var name = ""
    @State private var id = ""
    @State private var pw = ""
    @State private var terms = false
    @State private var consent = Consent()
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        Card {
            Text("Create your account").font(.title2.bold())
            TextField("Display name", text: $name).textFieldStyle(.roundedBorder)
            TextField("Email or mobile number", text: $id).textInputAutocapitalization(.never).keyboardType(.emailAddress).textFieldStyle(.roundedBorder)
            SecureField("Password (8+ characters)", text: $pw).textFieldStyle(.roundedBorder)

            SectionLabel("AGREEMENTS")
            Toggle("I accept the Terms of Use and Privacy Policy (required)", isOn: $terms).font(.subheadline.weight(.semibold))
            HStack {
                Link("Read Terms", destination: URL(string: session.baseURL + "/terms")!)
                Spacer()
                Link("Read Privacy Policy", destination: URL(string: session.baseURL + "/privacy")!)
            }.font(.footnote)
            SectionLabel("OPTIONAL, OFF BY DEFAULT")
            if Config.useCloudAI {
                optional("Cloud AI guide", "Lets your chat messages, visit history and saved notes be processed by the optional cloud AI provider.", $consent.ai)
                optional("Share location with the cloud guide", "Your live location is sent to the cloud guide only when you ask it for trip help. It is never stored on our server.", $consent.location)
            }
            optional("Public leaderboard", "Shows your display name and visit counts to other travellers.", $consent.leaderboard)
            Text("You can change these any time under Account.").font(.caption).foregroundStyle(.secondary)
            if let error { Text(error).font(.footnote).foregroundStyle(Theme.rose) }
            Button {
                busy = true; error = nil
                let ident = id.trimmingCharacters(in: .whitespaces)
                Task {
                    let r = await session.signup(identifier: ident, password: pw, name: name.trimmingCharacters(in: .whitespaces), consent: consent)
                    busy = false
                    if r.ok { created(ident, r.json?["devCode"] as? String) } else { error = friendly(r) }
                }
            } label: { Text(busy ? "Creating..." : "Create account").frame(maxWidth: .infinity) }
            .buttonStyle(.borderedProminent).tint(Theme.navy)
            .disabled(busy || !terms || name.trimmingCharacters(in: .whitespaces).count < 2 || id.isEmpty || pw.count < 8)
            Button("Back", action: back)
        }
    }

    private func optional(_ title: String, _ detail: String, _ binding: Binding<Bool>) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Toggle(title, isOn: binding).font(.subheadline.weight(.semibold))
            Text(detail).font(.caption).foregroundStyle(.secondary)
        }
    }
}

private struct VerifyForm: View {
    @Environment(AppSession.self) private var session
    let identifier: String
    let devCode: String?
    let back: () -> Void
    @State private var code = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        Card {
            Text("Verify your account").font(.title2.bold())
            Text("Enter the 6-digit code sent to \(identifier).").font(.subheadline)
            if let devCode { Text("Development server: your code is \(devCode)").font(.footnote.weight(.semibold)).foregroundStyle(Theme.teal) }
            TextField("Code", text: $code).keyboardType(.numberPad).textFieldStyle(.roundedBorder)
                .onChange(of: code) { _, new in code = String(new.filter(\.isNumber).prefix(6)) }
            if let error { Text(error).font(.footnote).foregroundStyle(Theme.rose) }
            Button {
                busy = true; error = nil
                Task { let r = await session.verify(identifier: identifier, code: code); busy = false; if !r.ok { error = friendly(r) } }
            } label: { Text(busy ? "Checking..." : "Verify").frame(maxWidth: .infinity) }
            .buttonStyle(.borderedProminent).tint(Theme.navy).disabled(busy || code.count != 6)
            Button("Back", action: back)
        }
    }
}
