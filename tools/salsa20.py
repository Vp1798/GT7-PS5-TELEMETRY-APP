"""Reference Salsa20/20 (same algorithm as app/.../Salsa20.kt). Used by fake_ps5.py and the self-test."""
import struct

M = 0xFFFFFFFF


def _rotl(v, n):
    return ((v << n) | (v >> (32 - n))) & M


def _qr(x, a, b, c, d):
    x[b] ^= _rotl((x[a] + x[d]) & M, 7)
    x[c] ^= _rotl((x[b] + x[a]) & M, 9)
    x[d] ^= _rotl((x[c] + x[b]) & M, 13)
    x[a] ^= _rotl((x[d] + x[c]) & M, 18)


def crypt(key: bytes, nonce: bytes, data: bytes) -> bytes:
    assert len(key) == 32 and len(nonce) == 8
    st = [0] * 16
    st[0], st[5], st[10], st[15] = 0x61707865, 0x3320646E, 0x79622D32, 0x6B206574
    k = struct.unpack("<8I", key)
    st[1:5] = k[0:4]
    st[11:15] = k[4:8]
    st[6], st[7] = struct.unpack("<2I", nonce)
    out = bytearray()
    counter = 0
    while len(out) < len(data):
        st[8], st[9] = counter & M, (counter >> 32) & M
        x = st[:]
        for _ in range(10):
            _qr(x, 0, 4, 8, 12); _qr(x, 5, 9, 13, 1); _qr(x, 10, 14, 2, 6); _qr(x, 15, 3, 7, 11)
            _qr(x, 0, 1, 2, 3); _qr(x, 5, 6, 7, 4); _qr(x, 10, 11, 8, 9); _qr(x, 15, 12, 13, 14)
        block = struct.pack("<16I", *[(x[i] + st[i]) & M for i in range(16)])
        out += block
        counter += 1
    return bytes(a ^ b for a, b in zip(data, out))


if __name__ == "__main__":
    key = bytes([0x80]) + bytes(31)
    ks = crypt(key, bytes(8), bytes(64)).hex().upper()
    want = ("E3BE8FDD8BECA2E3EA8EF9475B29A6E7003951E1097A5C38D23B7A5FAD9F6844"
            "B22C97559E2723C7CBBD3FE4FC8D9A0744652A83E72A9C461876AF4D7EF1A117")
    print("ECRYPT Set1 vector#0:", "PASS" if ks == want else "FAIL\n got  " + ks + "\n want " + want)
