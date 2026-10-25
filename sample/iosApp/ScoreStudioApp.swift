// Add this source to an iOS SwiftUI application target. See README for the Gradle
// embed-and-sign build phase; this is host source, not a generated Xcode project.
import SwiftUI
import ComposeTabSample

@main
struct ScoreStudioApp: App {
    var body: some Scene { WindowGroup { ScoreView().ignoresSafeArea(.keyboard) } }
}

struct ScoreView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
