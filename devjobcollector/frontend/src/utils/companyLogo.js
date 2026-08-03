const COMPANY_LOGOS = new Map([
  ['gitlab', 'https://about.gitlab.com/images/ico/favicon-192x192.png'],
  ['integrate', 'https://www.integrate.io/images/logos/logo.svg'],
  ['integrate.io', 'https://www.integrate.io/images/logos/logo.svg'],
]);

const normalizeCompanyName = (companyName = '') => companyName.trim().toLowerCase();

export const getCompanyLogoUrl = (job) => {
  if (job?.thumbnail) return job.thumbnail;
  return COMPANY_LOGOS.get(normalizeCompanyName(job?.companyName)) ?? null;
};

export const getCompanyInitials = (companyName = '') => {
  const normalized = companyName
    .replace(/\(주\)|㈜|주식회사|재단법인|사단법인/g, '')
    .trim();

  if (!normalized) return '🏢';

  const words = normalized.split(/\s+/).filter(Boolean);
  if (words.length >= 2) {
    return words.slice(0, 2).map((word) => word[0]).join('').toUpperCase();
  }
  return normalized.slice(0, 2).toUpperCase();
};
