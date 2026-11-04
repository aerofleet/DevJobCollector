-- Source: Woowahan R2611003, checked 2026-11-05. Preserve explicitly reviewed metadata.
UPDATE job_posts
SET job_category = NULL,
    experience = '신입·경력',
    experience_category = 'BOTH',
    experience_label = '신입·경력',
    experience_evidence = '신입·경력',
    experience_method = 'SOURCE',
    apply_qual = CONCAT(
      '구분 : 신입/경력\n계약기간 : 12개월\n',
      '[조직소개]\nB마트는 고객이 원하는 상품을 가장 빠르게 전달하는 퀵커머스의 선두주자입니다.\n',
      'B마트 MD실은 우아한형제들의 직매입 커머스를 책임집니다.\n',
      '합리적인 가격과 품질을 자랑하는 PB(자체 브랜드)를 통해 성장을 만들어내고 있습니다.\n',
      '단순히 물건을 파는 것을 넘어, B마트의 색이 담긴 상품으로 새로운 시장 트렌드를 함께 만들어 낼 분을 찾습니다.\n\n',
      '[업무내용]\n- B마트 PB 상품개발팀 MD 업무 지원\n- 상품 등록 및 상품 정보 관리\n',
      '- 생산·발주·입고·정산 및 공급사 커뮤니케이션\n- 신규 상품 기획 및 데이터 관리\n\n',
      '[지원자격]\n- 오피스툴(Google Sheets, Excel 등) 활용 역량\n',
      '- 공급사와 원활하게 커뮤니케이션할 수 있는 역량\n',
      '- 유관 부서와 협업하며 이해와 존중을 바탕으로 유연하게 커뮤니케이션할 수 있는 역량\n\n',
      '[우대사항]\n- 온·오프라인 유통사에서의 AMD 업무 경험\n',
      '- 퀵커머스 서비스에 대한 높은 이해도와 관심\n',
      '- 우아한형제들의 조직 문화를 이해하고 함께 성장하고자 하는 열정\n',
      '- 신규 업무와 과제에 적극적이고 능동적으로 임하는 태도'),
    version = version + 1
WHERE source_platform = 'WOOWAHAN'
  AND original_sn = 'woowahan:R2611003'
  AND reviewed_source_hash IS NULL;
