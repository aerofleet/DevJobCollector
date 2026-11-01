import importlib.util
import pathlib
import unittest
import urllib.error

path = pathlib.Path(__file__).with_name("collect-work-arrangement-snapshot.py")
spec = importlib.util.spec_from_file_location("collect_work_arrangement", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class CollectionTest(unittest.TestCase):
    def test_missing_description_and_fetch_failure_are_distinct(self):
        def get(url):
            if "search?" in url:
                return {"content": [{"id": 1}, {"id": 2}, {"id": 3}]}
            if url.endswith("/3"):
                raise urllib.error.URLError("offline")
            return {"id": int(url.rsplit("/", 1)[1]), "applyQual": "내용" if url.endswith("/1") else ""}
        rows = list(module.collect(3, 0.5, get=get, sleep=lambda _: None))
        self.assertEqual([row["status"] for row in rows], ["OK", "NO_DESCRIPTION", "FETCH_FAILED"])

    def test_rejects_duplicate_ids_and_mismatched_detail(self):
        with self.assertRaises(ValueError):
            list(module.collect(2, 0.5, get=lambda url: {"content": [{"id": 1}, {"id": 1}]}
                                if "search?" in url else {"id": 1}, sleep=lambda _: None))
        with self.assertRaises(ValueError):
            list(module.collect(1, 0.5, get=lambda url: {"content": [{"id": 1}]}
                                if "search?" in url else {"id": 2}, sleep=lambda _: None))

    def test_bounds(self):
        for limit, delay in [(0, 1), (501, 1), (2, 0.1)]:
            with self.assertRaises(ValueError):
                list(module.collect(limit, delay))


if __name__ == "__main__":
    unittest.main()
