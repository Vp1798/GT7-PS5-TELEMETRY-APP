#!/usr/bin/env python3
"""Pretends to be a PS5 running GT7 so you can test the app without the console.

Run on a computer on the same Wi-Fi as the phone:  python3 fake_ps5.py
In the app, enter this computer's IP address and tap Connect.
Sends encrypted 296-byte packets at 60 Hz after receiving the heartbeat on UDP 33739.
"""
import math, os, random, socket, struct, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import salsa20

KEY = b"Simulator Interface Packet GT7 ver 0.0"[:32]


def build(t, lap, last, best):
    p = bytearray(296)
    struct.pack_into("<i", p, 0x00, 0x47375330)
    ph = (t % 60) / 60
    speed = 45 + 28 * math.sin(2 * math.pi * ph * 3)
    rpm = 3500 + 4300 * (0.5 + 0.5 * math.sin(2 * math.pi * t / 5))
    struct.pack_into("<f", p, 0x3C, rpm)
    struct.pack_into("<f", p, 0x44, max(5.0, 100 - t * 0.12))
    struct.pack_into("<f", p, 0x48, 100.0)
    struct.pack_into("<f", p, 0x4C, speed)
    struct.pack_into("<f", p, 0x50, 1.0)
    for i in range(4):
        struct.pack_into("<f", p, 0x60 + 4 * i, 82 + 18 * math.sin(t / 7 + i))
    struct.pack_into("<i", p, 0x70, int(t * 60))
    struct.pack_into("<h", p, 0x74, lap)
    struct.pack_into("<h", p, 0x76, 5)
    struct.pack_into("<i", p, 0x78, best)
    struct.pack_into("<i", p, 0x7C, last)
    struct.pack_into("<H", p, 0x88, 6500)
    struct.pack_into("<H", p, 0x8A, 7800)
    struct.pack_into("<H", p, 0x8E, 1)  # car on track
    gear = max(1, min(6, 2 + int(speed / 14)))
    p[0x90] = gear
    p[0x91] = int(255 * max(0.0, math.sin(2 * math.pi * t / 5 + 0.6)))
    p[0x92] = int(204 * max(0.0, -math.sin(2 * math.pi * t / 5 + 0.6)))
    return p


def encrypt(plain: bytearray) -> bytes:
    iv1 = random.getrandbits(32)
    iv2 = iv1 ^ 0xDEADBEAF
    nonce = struct.pack("<II", iv2, iv1)
    enc = bytearray(salsa20.crypt(KEY, nonce, bytes(plain)))
    struct.pack_into("<I", enc, 0x40, iv1)  # IV travels in the clear at 0x40
    return bytes(enc)


def main():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("0.0.0.0", 33739))
    s.settimeout(0.5)
    print("Fake PS5 listening on UDP 33739. Enter this computer's IP in the app.")
    client = None
    t0 = time.time()
    last, best = -1, -1
    lap = 1
    while True:
        try:
            data, addr = s.recvfrom(64)
            if data[:1] == b"A":
                if client != addr[0]:
                    print("Heartbeat from", addr[0])
                client = addr[0]
        except socket.timeout:
            pass
        if client:
            t = time.time() - t0
            new_lap = 1 + int(t // 60)
            if new_lap != lap:
                last = 60000 + random.randint(-1500, 1500)
                best = last if best < 0 else min(best, last)
                lap = new_lap
            s.sendto(encrypt(build(t, lap, last, best)), (client, 33740))
            time.sleep(1 / 60)


if __name__ == "__main__":
    main()
