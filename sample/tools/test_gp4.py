"""Decoder contract and MIDI integration tests; no copyrighted fixture required."""
import importlib.util
import io
import tempfile
import unittest
import sys
sys.dont_write_bytecode = True
from pathlib import Path
from types import SimpleNamespace
import mido

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('gp4_decoder', ROOT / 'sample/tools/decode_gp4.py')
decoder = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(decoder)


class Gp4Tests(unittest.TestCase):
    def test_real_gp4_fixture_and_midi(self):
        result = decoder.decode(ROOT / 'sample/src/commonMain/composeResources/files/original-study.gp4')
        self.assertEqual(result['title'], 'Open strings study')
        self.assertEqual(len(result['tracks'][0]['measures']), 12)
        self.assertEqual(len(result['moments']), 96)
        import base64
        midi = mido.MidiFile(file=io.BytesIO(base64.b64decode(result['midiBase64'])))
        self.assertEqual(midi.ticks_per_beat, 960)
        self.assertEqual(sum(msg.type == 'note_on' for track in midi.tracks for msg in track), 96)
        ids = [beat['id'] for bar in result['tracks'][0]['measures'] for beat in bar['beats']]
        self.assertEqual(len(ids), len(set(ids)))

    def test_rejects_html_and_truncated_input(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'wrong.gp4'
            for content in [b'<html>not a score</html>', b'', b'\x18FICHIER GUITAR PRO v4.06' + b'\0' * 20]:
                path.write_bytes(content)
                with self.assertRaises(Exception):
                    decoder.decode(path)

    def test_repeat_order_and_alternate_endings(self):
        def bar(open=False, close=-1, alternative=0):
            return SimpleNamespace(isRepeatOpen=open, repeatClose=close, repeatAlternative=alternative)
        self.assertEqual(decoder.repeat_order([bar(True), bar(close=1)]), [0, 1, 0, 1])
        self.assertEqual(decoder.repeat_order([bar(True), bar(close=1, alternative=1), bar(alternative=2)]), [0, 1, 0, 2])
        self.assertEqual(decoder.repeat_order([bar(True), bar(True), bar(close=1), bar(close=1)]), [0, 1, 2, 1, 2, 3, 0, 1, 2, 1, 2, 3])


if __name__ == '__main__':
    unittest.main()
