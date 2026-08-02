// Renders the launch-screen crescent: same geometry as the app icon, but on a TRANSPARENT
// ground so one mark sits on the adaptive launch background.
//
// Two variants, because a gold crescent disappears on cream and a mauve one disappears on
// navy. Alpha is fine here — only the app icon itself must be opaque.
//
//   swift RenderMark.swift <out.png> light|dark
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

let size = 512
let s = CGFloat(size) / 100.0
let variant = CommandLine.arguments.count > 2 ? CommandLine.arguments[2] : "light"

func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: (100 - y) * s) }
func rect(cx: CGFloat, cy: CGFloat, r: CGFloat) -> CGRect {
    CGRect(x: (cx - r) * s, y: (100 - cy - r) * s, width: r * 2 * s, height: r * 2 * s)
}
func rgb(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: a)
}

let space = CGColorSpaceCreateDeviceRGB()
guard let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8,
                          bytesPerRow: 0, space: space,
                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { exit(1) }

// Light launch background (cream) needs the deep mauve→rose mark; dark (navy) needs gold→rose.
let stops: [CGColor] = variant == "dark"
    ? [rgb(0xFFF3DC), rgb(0xE8C97E), rgb(0xD9899E)]
    : [rgb(0x8C6BC4), rgb(0x6B3FA0), rgb(0xD4849A)]

// Same two-step clip as the icon: clip to the outer disc, then even-odd against the cut
// disc. Even-odd alone would fill the cut circle's overhang past the outer edge.
ctx.saveGState()
ctx.beginPath()
ctx.addEllipse(in: rect(cx: 47, cy: 50, r: 31))
ctx.clip()

ctx.beginPath()
ctx.addEllipse(in: rect(cx: 47, cy: 50, r: 31))
ctx.addEllipse(in: rect(cx: 69, cy: 33, r: 25))
ctx.clip(using: .evenOdd)

if let grad = CGGradient(colorsSpace: space, colors: stops as CFArray, locations: [0, 0.45, 1]) {
    ctx.drawLinearGradient(grad, start: p(14, 16), end: p(82, 92),
                           options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
}
ctx.restoreGState()

guard let image = ctx.makeImage() else { exit(1) }
let out = URL(fileURLWithPath: CommandLine.arguments[1])
guard let dest = CGImageDestinationCreateWithURL(out as CFURL, UTType.png.identifier as CFString, 1, nil)
else { exit(1) }
CGImageDestinationAddImage(dest, image, nil)
if !CGImageDestinationFinalize(dest) { exit(1) }
print("wrote \(out.lastPathComponent) [\(variant)]")
