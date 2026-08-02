// Renders the Cycluna app icon: a gold→rose crescent on the brand night sky.
//
// Written with an opaque (noneSkipLast) bitmap so the PNG carries no alpha channel —
// App Store Connect rejects icons that do. Square, full-bleed, no pre-rounded corners:
// iOS applies its own mask.
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// swift RenderIcon.swift <out.png> [night|day]
//   night — gold crescent on the brand night sky (default)
//   day   — mauve crescent on brand cream, matching the light-only app and launch screen
let variant = CommandLine.arguments.count > 2 ? CommandLine.arguments[2] : "night"

let size = 1024
let s = CGFloat(size) / 100.0          // design space is 100×100, y-down like SVG

func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: (100 - y) * s) }
func rect(cx: CGFloat, cy: CGFloat, r: CGFloat) -> CGRect {
    CGRect(x: (cx - r) * s, y: (100 - cy - r) * s, width: r * 2 * s, height: r * 2 * s)
}
func rgb(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: a)
}

let space = CGColorSpaceCreateDeviceRGB()
guard let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8,
                          bytesPerRow: 0, space: space,
                          bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
    FileHandle.standardError.write("could not create context\n".data(using: .utf8)!)
    exit(1)
}

// --- Night sky -------------------------------------------------------------
let bgStops: [CGColor] = variant == "day"
    ? [rgb(0xFFFBF6), rgb(0xF2E7DC)]
    : [rgb(0x2A2359), rgb(0x0F0F2D)]
if let bg = CGGradient(colorsSpace: space, colors: bgStops as CFArray, locations: [0, 1]) {
    ctx.drawRadialGradient(bg, startCenter: p(50, 42), startRadius: 0,
                           endCenter: p(50, 42), endRadius: 78 * s,
                           options: [.drawsAfterEndLocation])
}

// --- Two stars. Deliberately few and large: a dense field turns to grain below 40pt.
for (x, y, r, alpha) in (variant == "day" ? [] : [(80.0, 22.0, 1.9, 0.95), (22.0, 76.0, 1.5, 0.8)]) as [(CGFloat, CGFloat, CGFloat, CGFloat)] {
    ctx.setFillColor(rgb(0xF3E4C2, alpha))
    ctx.fillEllipse(in: rect(cx: x, cy: y, r: r))
}

// --- Soft glow behind the crescent ----------------------------------------
// Start at radius 0: beginning the gradient partway out leaves a hard edge that shows as a
// ghost ring through the crescent's cut-away.
if let glow = CGGradient(colorsSpace: space,
                         colors: variant == "day"
                             ? [rgb(0x6B3FA0, 0.10), rgb(0x6B3FA0, 0)] as CFArray
                             : [rgb(0xE8C97E, 0.22), rgb(0xE8C97E, 0)] as CFArray,
                         locations: [0, 1]) {
    ctx.drawRadialGradient(glow, startCenter: p(47, 50), startRadius: 0,
                           endCenter: p(47, 50), endRadius: 48 * s,
                           options: [.drawsBeforeStartLocation])
}

// --- Crescent: outer disc minus an offset disc, filled with the brand gradient ---
// Two nested clips. An even-odd clip ALONE gives the XOR of the two discs — the cut circle
// reaches past the outer edge, so its overhang would be filled too. Clipping to the outer
// disc first intersects that away, leaving outer-minus-inner: a real crescent.
ctx.saveGState()
ctx.beginPath()
ctx.addEllipse(in: rect(cx: 47, cy: 50, r: 31))
ctx.clip()

ctx.beginPath()
ctx.addEllipse(in: rect(cx: 47, cy: 50, r: 31))
ctx.addEllipse(in: rect(cx: 69, cy: 33, r: 25))
ctx.clip(using: .evenOdd)

if let grad = CGGradient(colorsSpace: space,
                         colors: (variant == "day"
                             ? [rgb(0x8C6BC4), rgb(0x6B3FA0), rgb(0xD4849A)]
                             : [rgb(0xFFF3DC), rgb(0xE8C97E), rgb(0xD9899E)]) as CFArray,
                         locations: [0, 0.45, 1]) {
    ctx.drawLinearGradient(grad, start: p(14, 16), end: p(82, 92),
                           options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
}
ctx.restoreGState()

// --- Write ----------------------------------------------------------------
guard let image = ctx.makeImage() else { exit(1) }
let out = URL(fileURLWithPath: CommandLine.arguments[1])
guard let dest = CGImageDestinationCreateWithURL(out as CFURL, UTType.png.identifier as CFString, 1, nil) else {
    exit(1)
}
CGImageDestinationAddImage(dest, image, nil)
if !CGImageDestinationFinalize(dest) { exit(1) }
print("wrote \(out.path)")
