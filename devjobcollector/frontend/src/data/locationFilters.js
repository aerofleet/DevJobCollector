export const LOCATION_GROUPS = [
  { label: '국내', options: [
    ['domestic', '국내 전체'], ['전국', '전국'],
    ...['서울', '경기', '인천', '부산', '대구', '대전', '광주', '울산', '세종',
      '강원', '충북', '충남', '전북', '전남', '경북', '경남', '제주'].map(value => [value, value]),
  ] },
  { label: '해외', options: [['overseas', '해외 전체']] },
  { label: '아시아', options: ['일본', '중국', '대만', '홍콩', '싱가포르', '베트남', '태국', '말레이시아', '인도네시아', '필리핀', '인도'].map(value => [value, value]) },
  { label: '미주', options: ['미국', '캐나다', '멕시코', '브라질'].map(value => [value, value]) },
  { label: '유럽', options: ['영국', '독일', '프랑스', '네덜란드', '아일랜드', '스위스', '스웨덴', '스페인', '폴란드'].map(value => [value, value]) },
  { label: '오세아니아', options: ['호주', '뉴질랜드'].map(value => [value, value]) },
  { label: '중동', options: ['아랍에미리트', '사우디아라비아', '이스라엘'].map(value => [value, value]) },
  { label: '근무 방식', options: [['Remote', '원격·재택']] },
];

export const locationLabel = value => LOCATION_GROUPS.flatMap(group => group.options)
  .find(([key]) => key === value)?.[1] ?? value;
