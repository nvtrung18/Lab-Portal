import { ArrowUpRight, BookOpen } from 'lucide-react';
import type { ScholarProfile } from '../../../../../../contracts/website-content';
import { formatPublicDate } from '../lib/utils';
import { useLab } from './lab-provider';
import Link from './site-link';

export function ScholarProfileDetails({ profile }: { profile: ScholarProfile }) {
  const { text, l, locale } = useLab();
  const metrics = profile.metrics;
  const rows = metrics ? [
    [l('Citations', 'Trích dẫn'), metrics.citations, metrics.recentCitations],
    ['h-index', metrics.hIndex, metrics.recentHIndex],
    ['i10-index', metrics.i10Index, metrics.recentI10Index],
  ] as const : [];
  const value = (number: number | null | undefined) => number ?? '—';
  return <div className="scholar-profile">
    <div className="detail-body-grid">
      <div>
        <h2>{l('Academic profile', 'Hồ sơ học thuật')}</h2>
        {text(profile.affiliation) && <p>{text(profile.affiliation)}</p>}
        {profile.interests.length > 0 && <>
          <h3>{l('Research interests', 'Lĩnh vực nghiên cứu')}</h3>
          <ul className="scholar-interests">{profile.interests.map((interest, index) =>
            <li key={index}>{text(interest)}</li>)}</ul>
        </>}
        <div className="scholar-links">
          <Link href={profile.url} className="button button-primary" target="_blank" rel="noopener noreferrer">
            Google Scholar <ArrowUpRight size={16} aria-hidden="true" />
          </Link>
          {profile.homepageUrl && <Link href={profile.homepageUrl} className="text-link" target="_blank" rel="noopener noreferrer">
            {l('Personal website', 'Trang cá nhân')} <ArrowUpRight size={16} aria-hidden="true" />
          </Link>}
        </div>
        <p className="scholar-source-note">
          {l('Source: Google Scholar. Retrieved on', 'Nguồn: Google Scholar. Lấy dữ liệu ngày')}{' '}
          <time dateTime={profile.retrievedOn}>{formatPublicDate(profile.retrievedOn, locale)}</time>.
          {' '}{l('Metrics may change over time.', 'Các chỉ số có thể thay đổi theo thời gian.')}
        </p>
      </div>
      {metrics && <aside className="detail-aside scholar-metrics">
        <h2>{l('Citation metrics', 'Chỉ số trích dẫn')}</h2>
        <table>
          <thead><tr>
            <th scope="col"><span className="sr-only">{l('Metric', 'Chỉ số')}</span></th>
            <th scope="col">{l('All time', 'Tất cả')}</th>
            {metrics.sinceYear != null && <th scope="col">{l('Since', 'Từ')} {metrics.sinceYear}</th>}
          </tr></thead>
          <tbody>{rows.map(([label, total, recent]) => <tr key={label}>
            <th scope="row">{label}</th><td>{value(total)}</td>
            {metrics.sinceYear != null && <td>{value(recent)}</td>}
          </tr>)}</tbody>
        </table>
      </aside>}
    </div>
    {profile.publications.length > 0 && <section className="scholar-publications">
      <div className="section-heading">
        <h2><BookOpen size={22} aria-hidden="true" />{l('Selected publications', 'Bài viết từ Google Scholar')}</h2>
        <Link href={profile.url} className="text-link" target="_blank" rel="noopener noreferrer">
          {l('View all on Scholar', 'Xem đầy đủ trên Scholar')} <ArrowUpRight size={16} aria-hidden="true" />
        </Link>
      </div>
      <p className="scholar-source-note">{l('Selected articles from this profile. Author and venue text follows the source and may be abbreviated.', 'Một số bài viết trong hồ sơ. Tên tác giả và nơi công bố giữ theo nguồn, có thể được viết rút gọn.')}</p>
      <div className="scholar-table-wrap" role="region" aria-label={l('Selected publication list', 'Danh sách bài viết')} tabIndex={0}>
        <table>
          <thead><tr>
            <th scope="col">{l('Title / authors / venue', 'Tiêu đề / tác giả / nơi công bố')}</th>
            <th scope="col">{l('Citations', 'Trích dẫn')}</th>
            <th scope="col">{l('Year', 'Năm')}</th>
          </tr></thead>
          <tbody>{profile.publications.map((publication, index) => <tr key={`${publication.url}-${index}`}>
            <td>
              <Link href={publication.url} target="_blank" rel="noopener noreferrer">{publication.title}<ArrowUpRight size={14} aria-hidden="true" /></Link>
              {publication.authors && <p>{publication.authors}</p>}
              {publication.venue && <p>{publication.venue}</p>}
            </td>
            <td>{value(publication.citations)}</td><td>{value(publication.year)}</td>
          </tr>)}</tbody>
        </table>
      </div>
    </section>}
  </div>;
}
