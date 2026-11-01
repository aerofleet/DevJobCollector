import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).with_name("predict-work-arrangement.py")
spec = importlib.util.spec_from_file_location("work_arrangement_predict", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PredictionTest(unittest.TestCase):
    def test_two_distinct_signals_for_each_class(self):
        cases = [("파견 근무", "고객사 근무", "DISPATCH"),
                 ("SI 프로젝트", "고객사 시스템 구축", "SI_OUTSOURCING"),
                 ("백엔드", "자사 서비스 개발", "IN_HOUSE")]
        for title, body, expected in cases:
            with self.subTest(expected=expected):
                self.assertEqual(module.predict(title, body)[0], expected)

    def test_isolated_or_repeated_keywords_do_not_classify(self):
        for body in ("파견 파견 파견", "SI 개발 SI 개발", "고객사", "자사 서비스"):
            self.assertEqual(module.predict("", body)[0], "UNKNOWN")

    def test_negation_and_conflicting_signals_are_unknown(self):
        for body in ("고객사 파견 근무 없음", "파견하지 않습니다. 고객사 파견 근무",
                     "고객사 파견 근무 및 자사 서비스 개발"):
            self.assertEqual(module.predict("", body)[0], "UNKNOWN")

    def test_missing_text_is_unknown(self):
        self.assertEqual(module.predict(None, None)[0], "UNKNOWN")


if __name__ == "__main__":
    unittest.main()
