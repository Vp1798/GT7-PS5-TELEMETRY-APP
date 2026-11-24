"""Round-trip test: build -> encrypt (like the PS5) -> decrypt with the same logic as Gt7Packet.kt."""
import os, struct, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import salsa20, fake_ps5

plain = fake_ps5.build(12.3, 2, 61234, 60500)
raw = fake_ps5.encrypt(plain)
iv1 = struct.unpack_from("<I", raw, 0x40)[0]
nonce = struct.pack("<II", iv1 ^ 0xDEADBEAF, iv1)
dec = salsa20.crypt(fake_ps5.KEY, nonce, raw)
assert struct.unpack_from("<i", dec, 0)[0] == 0x47375330, "magic mismatch"
for off, name, fmt in [(0x3C, "rpm", "<f"), (0x4C, "speed m/s", "<f"), (0x74, "lap", "<h"), (0x78, "best ms", "<i"), (0x7C, "last ms", "<i")]:
    a, b = struct.unpack_from(fmt, plain, off)[0], struct.unpack_from(fmt, dec, off)[0]
    assert a == b, name
    print(f"{name}: {b}")
print("roundtrip PASS")
