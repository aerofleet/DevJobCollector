import importlib.util
import pathlib
import csv
import subprocess
import sys
import tempfile
import unittest

path = pathlib.Path(__file__).with_name("match-pension-candidates.py")
spec = importlib.util.spec_from_file_location("pension_candidates", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PensionCandidatesTest(unittest.TestCase):
    def rows(self, companies, workplaces, aliases=()):
        return list(module.candidate_rows(companies, workplaces, "name", "month", "count", aliases))

    def test_duplicate_names_always_require_review(self):
        rows = self.rows([{"id": "1", "name": "테스트 회사"}], [
            {"name": "테스트회사", "month": "202609", "count": "23"},
            {"name": "테스트회사", "month": "202609", "count": "42"}])
        self.assertEqual(len(rows), 2)
        self.assertEqual({row["status"] for row in rows}, {"REVIEW_REQUIRED"})
        self.assertEqual({row["candidate_count"] for row in rows}, {2})
        self.assertEqual({row["source_record"] for row in rows}, {2, 3})
        self.assertEqual({row["source_month"] for row in rows}, {"2026-09"})

    def test_absence_and_invalid_data_are_not_zero(self):
        rows = self.rows([{"id": "1", "name": "없는 회사"}, {"id": "2", "name": "회사"}],
                         [{"name": "회사", "month": "202613", "count": "-1"}])
        self.assertEqual(rows[0]["status"], "NO_MATCH")
        self.assertEqual(rows[0]["subscribers"], "")
        self.assertEqual(rows[1]["source_month"], "")
        self.assertEqual(rows[1]["subscribers"], "")

    def test_legal_form_is_not_discarded(self):
        self.assertNotEqual(module.normalize_name("회사"), module.normalize_name("회사(주)"))

    def test_source_year_month_with_hyphen(self):
        rows = self.rows([{"id": "1", "name": "회사"}],
                         [{"name": "회사", "month": "2026-05", "count": "10"}])
        self.assertEqual(rows[0]["source_month"], "2026-05")

    def test_duplicate_company_id_rejected(self):
        with self.assertRaises(ValueError):
            self.rows([{"id": "1", "name": "가"}, {"id": "1", "name": "나"}], [])

    def test_alias_collisions_and_duplicate_aliases_remain_reviewable(self):
        companies = [{"id": "1", "name": "Alpha"}, {"id": "2", "name": "Beta"}]
        aliases = [{"company_id": "1", "alias": "한글명"}, {"company_id": "1", "alias": "한글명"},
                   {"company_id": "2", "alias": "한글명"}]
        rows = self.rows(companies, [{"name": "한글명", "month": "2026-05", "count": "10"}], aliases)
        self.assertEqual(len(rows), 2)
        self.assertEqual({r["status"] for r in rows}, {"REVIEW_REQUIRED"})
        self.assertEqual({r["competing_companies"] for r in rows}, {2})
        self.assertEqual({r["match_type"] for r in rows}, {"ALIAS"})

    def test_alias_must_reference_known_company(self):
        with self.assertRaises(ValueError):
            self.rows([{"id": "1", "name": "가"}], [], [{"company_id": "2", "alias": "나"}])

    def test_spreadsheet_formulas_are_escaped(self):
        self.assertEqual(module.safe_cell(" =1+1"), "' =1+1")

    def test_cli_reads_cp949_source_and_writes_review_candidates(self):
        with tempfile.TemporaryDirectory() as directory:
            company_file = pathlib.Path(directory) / "companies.csv"
            source_file = pathlib.Path(directory) / "source.csv"
            alias_file = pathlib.Path(directory) / "aliases.csv"
            company_file.write_text("id,name\n1,테스트회사\n2,없는회사\n", encoding="utf-8")
            alias_file.write_text("company_id,alias\n1,다른이름\n", encoding="utf-8")
            source_file.write_text("사업장명,자료생성년월(설명),가입자수(고지인원 포함)\n다른이름,202609,23\n", encoding="cp949")
            result = subprocess.run([sys.executable, str(path), str(company_file), str(source_file),
                                     "--encoding", "cp949", "--aliases", str(alias_file)], capture_output=True, check=True)
            rows = list(csv.DictReader(result.stdout.decode("utf-8").splitlines()))
            self.assertEqual([(row["status"], row["source_record"]) for row in rows],
                             [("REVIEW_REQUIRED", "2"), ("NO_MATCH", "")])
            self.assertEqual(rows[0]["match_type"], "ALIAS")


if __name__ == "__main__":
    unittest.main()
