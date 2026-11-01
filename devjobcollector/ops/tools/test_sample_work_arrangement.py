import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).with_name("sample-work-arrangement.py")
spec = importlib.util.spec_from_file_location("work_arrangement_sample", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SamplingTest(unittest.TestCase):
    def test_seeded_balanced_and_independent_of_input_order(self):
        rows = [{"id": str(i), "source": "A" if i < 8 else "B", "title": "개발", "description": "내용"}
                for i in range(12)]
        first = module.sample(rows, 6, 42)
        self.assertEqual(first, module.sample(reversed(rows), 6, 42))
        self.assertEqual(sum(row["source"] == "A" for row in first), 3)
        self.assertTrue(all(row["adjudicated_label"] == "" for row in first))

    def test_missing_description_excluded_and_insufficient_size_fails(self):
        rows = [{"id": "1", "source": "A", "title": "제목", "description": ""}]
        with self.assertRaises(ValueError):
            module.sample(rows, 1, 1)

    def test_duplicate_id_rejected_even_when_one_row_is_ineligible(self):
        rows = [{"id": "1", "source": "A", "title": "제목", "description": ""},
                {"id": "1", "source": "B", "title": "제목", "description": "내용"}]
        with self.assertRaises(ValueError):
            module.sample(rows, 1, 1)


if __name__ == "__main__":
    unittest.main()
