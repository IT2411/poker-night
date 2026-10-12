import SwiftUI
import SharedKit

struct ContentView: View {
    let buildInfo = Environment.shared.getBuildInfo()

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "suit.spade.fill")
                .imageScale(.large)
                .font(.system(size: 48))
                .foregroundColor(.green)
            Text(buildInfo)
                .font(.headline)
                .multilineTextAlignment(.center)
                .padding()
        }
        .padding()
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}