"""Exercise dependency server throttling without external network calls or real delays."""
import hashlib
import io
import json
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import prepare_media3


class DependencyDownloadTest(unittest.TestCase):
    url = 'https://example.invalid/player.aar'

    def failure(self, status, retry_after=None):
        headers = {'Retry-After': retry_after} if retry_after is not None else {}
        return HTTPError(self.url, status, 'fixture', headers, io.BytesIO())

    def test_recovers_after_server_delay(self):
        with patch.object(prepare_media3.urllib.request, 'urlopen', side_effect=[
            self.failure(429, '7'), io.BytesIO(b'pinned artifact')
        ]) as fetch, patch.object(prepare_media3.time, 'sleep') as sleep:
            self.assertEqual(prepare_media3.download(self.url), b'pinned artifact')
            self.assertEqual(fetch.call_count, 2)
            sleep.assert_called_once_with(7)

    def test_central_throttling_uses_the_same_artifact_on_the_mirror(self):
        url = 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar'
        with patch.object(prepare_media3.urllib.request, 'urlopen', side_effect=[
            self.failure(429, '600'), io.BytesIO(b'artifact')
        ]) as fetch:
            self.assertEqual(prepare_media3.download(url), b'artifact')
            self.assertEqual(fetch.call_args_list[1].args[0],
                             'https://maven-central.storage-download.googleapis.com/maven2/org/json/json/20240303/json-20240303.jar')

    def test_http_date_and_invalid_header(self):
        with patch.object(prepare_media3.time, 'time', return_value=0):
            self.assertEqual(prepare_media3.retry_delay('Thu, 01 Jan 1970 00:00:12 GMT', 3), 12)
        self.assertEqual(prepare_media3.retry_delay('unavailable', 3), 3)

    def test_bounded_retries(self):
        errors = [self.failure(503) for _ in range(4)]
        with patch.object(prepare_media3.urllib.request, 'urlopen', side_effect=errors) as fetch, \
                patch.object(prepare_media3.time, 'sleep') as sleep:
            with self.assertRaises(HTTPError):
                prepare_media3.download(self.url)
            self.assertEqual(fetch.call_count, 4)
            self.assertEqual([args[0] for args, _ in sleep.call_args_list], [3, 6, 12])

    def test_response_is_verified_before_caching(self):
        with TemporaryDirectory() as folder, patch.object(prepare_media3, 'ROOT', Path(folder)):
            root = Path(folder)
            (root / 'tools').mkdir()
            artifact = {'group': 'test', 'artifact': 'player', 'version': '1', 'type': 'jar',
                        'url': self.url, 'sha256': hashlib.sha256(b'correct').hexdigest()}
            (root / 'tools/media3-deps.json').write_text(json.dumps({'artifacts': [artifact]}), encoding='utf-8')
            with patch.object(prepare_media3, 'download', return_value=b'corrupt'):
                with self.assertRaisesRegex(RuntimeError, 'checksum mismatch'):
                    prepare_media3.main()
            self.assertEqual(list((root / '.build-deps').iterdir()), [])
            with patch.object(prepare_media3, 'download', return_value=b'correct') as fetch:
                prepare_media3.main()
                prepare_media3.main()
                fetch.assert_called_once_with(self.url)
            self.assertEqual((root / 'out/media3/libs/test_player_1.jar').read_bytes(), b'correct')

    def test_permanent_errors_and_long_cooldowns_stop(self):
        for error in (self.failure(404), self.failure(429, '600')):
            with self.subTest(code=error.code), patch.object(prepare_media3.urllib.request, 'urlopen', side_effect=error) as fetch, \
                    patch.object(prepare_media3.time, 'sleep') as sleep:
                with self.assertRaises(HTTPError):
                    prepare_media3.download(self.url)
                fetch.assert_called_once()
                sleep.assert_not_called()


if __name__ == '__main__':
    unittest.main()
