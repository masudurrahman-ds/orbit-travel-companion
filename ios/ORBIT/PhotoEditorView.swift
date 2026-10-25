import SwiftUI
import PhotosUI
import Photos
import CoreImage
import CoreImage.CIFilterBuiltins

enum PhotoFilter: String, CaseIterable, Identifiable {
    case none = "Original", mono = "Mono", sepia = "Sepia", vivid = "Vivid", fade = "Fade"
    var id: String { rawValue }
}

struct PhotoEdits: Equatable {
    var brightness: Double = 0     // -0.5 ... 0.5
    var contrast: Double = 1       // 0.5 ... 1.5
    var saturation: Double = 1     // 0 ... 2
    var warmth: Double = 0         // -1 ... 1
    var filter: PhotoFilter = .none
    var rotation = 0               // degrees, multiples of 90
    var squareCrop = false
}

/// On-device photo editing with Core Image. Nothing here uploads the picture anywhere.
enum PhotoEditor {
    private static let context = CIContext()

    static func render(_ image: UIImage, _ e: PhotoEdits) -> UIImage? {
        guard var ci = CIImage(image: image) else { return nil }
        ci = ci.oriented(forExifOrientation: Int32(image.imageOrientation.exifValue))
        for _ in 0..<((e.rotation / 90) % 4) { ci = ci.oriented(.right) }
        if e.squareCrop {
            let s = min(ci.extent.width, ci.extent.height)
            ci = ci.cropped(to: CGRect(x: ci.extent.midX - s / 2, y: ci.extent.midY - s / 2, width: s, height: s))
        }
        let controls = CIFilter.colorControls()
        controls.inputImage = ci
        controls.brightness = Float(e.brightness)
        controls.contrast = Float(e.contrast)
        controls.saturation = Float(e.saturation * (e.filter == .vivid ? 1.4 : 1))
        ci = controls.outputImage ?? ci
        if e.warmth != 0 {
            let t = CIFilter.temperatureAndTint()
            t.inputImage = ci
            t.neutral = CIVector(x: 6500, y: 0)
            t.targetNeutral = CIVector(x: 6500 - CGFloat(e.warmth) * 1500, y: 0)
            ci = t.outputImage ?? ci
        }
        switch e.filter {
        case .none, .vivid: break
        case .mono: let f = CIFilter.photoEffectMono(); f.inputImage = ci; ci = f.outputImage ?? ci
        case .sepia: let f = CIFilter.sepiaTone(); f.inputImage = ci; f.intensity = 0.9; ci = f.outputImage ?? ci
        case .fade: let f = CIFilter.photoEffectFade(); f.inputImage = ci; ci = f.outputImage ?? ci
        }
        guard let cg = context.createCGImage(ci, from: ci.extent) else { return nil }
        return UIImage(cgImage: cg)
    }

    static func save(_ image: UIImage, completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.shared().performChanges({ PHAssetChangeRequest.creationRequestForAsset(from: image) }) { ok, _ in
            DispatchQueue.main.async { completion(ok) }
        }
    }
}

private extension UIImage.Orientation {
    var exifValue: Int {
        switch self {
        case .up: return 1
        case .upMirrored: return 2
        case .down: return 3
        case .downMirrored: return 4
        case .leftMirrored: return 5
        case .right: return 6
        case .rightMirrored: return 7
        case .left: return 8
        @unknown default: return 1
        }
    }
}

struct PhotoEditorView: View {
    var embedded = false
    @State private var item: PhotosPickerItem?
    @State private var source: UIImage?
    @State private var preview: UIImage?
    @State private var edits = PhotoEdits()
    @State private var message: String?

    var body: some View {
        NavWrap(embedded: embedded) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Edits happen on your phone. Pictures are never uploaded.").font(.footnote)
                    PhotosPicker(selection: $item, matching: .images) { Text("Choose photo").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent).tint(Theme.navy)
                    if let preview, let shown = PhotoEditor.render(preview, edits) {
                        Image(uiImage: shown).resizable().scaledToFit().frame(maxHeight: 420).clipShape(RoundedRectangle(cornerRadius: 16)).frame(maxWidth: .infinity)
                        SectionLabel("FILTER")
                        Picker("Filter", selection: $edits.filter) { ForEach(PhotoFilter.allCases) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented)
                        slider("Brightness", $edits.brightness, -0.5...0.5)
                        slider("Contrast", $edits.contrast, 0.5...1.5)
                        slider("Colour", $edits.saturation, 0...2)
                        slider("Warmth", $edits.warmth, -1...1)
                        HStack {
                            Button("Rotate") { edits.rotation = (edits.rotation + 90) % 360 }.buttonStyle(.bordered)
                            Toggle("Square crop", isOn: $edits.squareCrop)
                        }
                        HStack {
                            Button("Reset") { edits = PhotoEdits() }.buttonStyle(.bordered)
                            Button("Save to Photos") { save() }.buttonStyle(.borderedProminent).tint(Theme.navy)
                        }
                    }
                    if let message { Text(message).fontWeight(.semibold) }
                }
                .padding(16)
            }
            .navigationTitle("Photo editor")
            .onChange(of: item) { _, new in load(new) }
        }
    }

    private func slider(_ label: String, _ value: Binding<Double>, _ range: ClosedRange<Double>) -> some View {
        VStack(alignment: .leading) { Text(label).font(.footnote); Slider(value: value, in: range) }
    }

    private func load(_ item: PhotosPickerItem?) {
        guard let item else { return }
        Task {
            guard let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) else { message = "Could not open that picture."; return }
            source = img
            preview = downscale(img, maxDim: 1080)
            edits = PhotoEdits(); message = nil
        }
    }

    private func downscale(_ img: UIImage, maxDim: CGFloat) -> UIImage {
        let scale = min(1, maxDim / max(img.size.width, img.size.height))
        guard scale < 1 else { return img }
        let size = CGSize(width: img.size.width * scale, height: img.size.height * scale)
        return UIGraphicsImageRenderer(size: size).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
    }

    private func save() {
        guard let source, let out = PhotoEditor.render(source, edits) else { return }
        PhotoEditor.save(out) { ok in message = ok ? "Saved to your Photos." : "Could not save. Allow adding photos in Settings." }
    }
}
