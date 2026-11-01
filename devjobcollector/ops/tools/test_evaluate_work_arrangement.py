import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).with_name("evaluate-work-arrangement.py")
spec = importlib.util.spec_from_file_location("work_arrangement_evaluation", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class EvaluationTest(unittest.TestCase):
    def test_unknown_predictions_do_not_inflate_precision_or_sample_gate(self):
        rows = [{"id": str(i), "expected": "DISPATCH", "predicted": "UNKNOWN"}
                for i in range(200)]
        rows[0]["predicted"] = "DISPATCH"
        result = module.evaluate(rows)
        self.assertEqual(result["precision"], 1.0)
        self.assertEqual(result["classified"], 1)
        self.assertFalse(result["gate_passed"])

    def test_gate_and_false_positives(self):
        labels = ["IN_HOUSE", "UNKNOWN", "SI_OUTSOURCING", "DISPATCH"]
        rows = []
        for i in range(200):
            expected = labels[i % 4]
            predicted = "DISPATCH" if expected == "SI_OUTSOURCING" and i < 16 else expected
            hard_negative = expected in {"IN_HOUSE", "UNKNOWN"} and i < 120
            rows.append({"id": str(i), "cluster_id": str(i), "expected": expected,
                         "predicted": predicted, "reviewer_a": expected, "reviewer_b": expected,
                         "hard_negative": str(hard_negative).lower()})
        result = module.evaluate(rows)
        self.assertTrue(result["gate_passed"])

    def test_rejects_duplicate_or_unlabelled_rows(self):
        with self.assertRaises(ValueError):
            module.evaluate([{"id": "1", "expected": "", "predicted": "UNKNOWN"}])
        with self.assertRaises(ValueError):
            module.evaluate([{"id": "1", "expected": "UNKNOWN", "predicted": "UNKNOWN"}] * 2)


if __name__ == "__main__":
    unittest.main()
