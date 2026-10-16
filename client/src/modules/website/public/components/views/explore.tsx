import { useState } from "react";
import {
  ArrowUpRight,
  FileText,
  Code2,
  Link2,
  Quote,
  Copy,
  Check,
} from "lucide-react";
import Link from "../site-link";
import { useLab, roleVi } from "../lab-provider";
import {
  ResearchCard,
  MemberCard,
  ProjectCard,
  NewsCard,
  PageHeading,
  EmptyState,
} from "../shared";
import { Button } from "../ui/button";
import { Dialog } from "../ui/dialog";
import type { Publication } from "../../lib/types";
import { ContentSearch } from "../content-search";
import { SearchInput } from "../search-input";
import { memberRoleGroups, matchesMemberRole, partitionMemberDisplayGroups } from '../../../member-roles';
export function ResearchView() {
  const { l, data } = useLab();
  return <>
    <PageHeading eyebrow={l("Research", "Nghiên cứu")} title={l("Questions that connect us.", "Những câu hỏi kết nối chúng ta.")} description="" showSections={false} />
    <ContentSearch kind="research" records={data.research} renderRecords={items => <div className="cards-grid two">{items.map((item, index) => <ResearchCard key={item.id} item={item} index={index} />)}</div>} />
  </>;
}
export function PeopleView() {
  const { l, text, data } = useLab();
  const [role, setRole] = useState("all");
  const [query, setQuery] = useState("");
  const otherGroup = {
    role: 'other', label: { en: 'Other members', vi: 'Thành viên khác' },
    title: { en: 'Other Members', vi: 'Thành viên khác' },
    showWhenEmpty: false,
  };
  const groups = [
    ...memberRoleGroups.filter(group => group.role !== 'Guest'),
    otherGroup,
    ...memberRoleGroups.filter(group => group.role === 'Guest'),
  ];
  const filtered = data.members.filter(
    (item) =>
      (role === "all" || matchesMemberRole(item.role, role)) &&
      `${text(item.title)} ${text(item.description)}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  const visibleGroups = groups.map(group => ({
    ...group,
    members: filtered.filter(item => matchesMemberRole(item.role, group.role)),
  })).filter(group => group.members.length || (group.showWhenEmpty &&
    (role === group.role || (role === 'all' && !query.trim()))));
  const { hierarchy, additional, collaborators } = partitionMemberDisplayGroups(visibleGroups);
  const researchCount = [...hierarchy, ...additional].reduce((count, group) => count + group.members.length, 0);
  const renderTier = (group: typeof visibleGroups[number], hierarchical: boolean) => {
    const headingId = `member-group-${group.role.toLowerCase().replace(/ /g, '-')}`;
    return <section className={hierarchical ? 'member-tier' : 'member-additional'} key={group.role} aria-labelledby={headingId}>
      <div className="member-tier-heading">
        <h3 id={headingId}>{text(group.title)}</h3>
        <span>{group.members.length} {l('members', 'thành viên')}</span>
      </div>
      {group.members.length ? <div className={`member-tier-grid member-tier-grid-${Math.min(group.members.length, 6)}`}>
        {group.members.map(item => <MemberCard item={item} key={item.id} />)}
      </div> : <p className="member-tier-empty" role="status">{query.trim()
        ? l('No matching master\'s students.', 'Không có học viên cao học phù hợp.')
        : l('No master\'s students have been added yet.', 'Chưa có học viên cao học trong danh sách.')}</p>}
    </section>;
  };
  return (
    <>
      <PageHeading
        eyebrow={l("People", "Thành viên")}
        title={l(
          "Many minds. One shared curiosity.",
          "Nhiều tư duy. Chung niềm khám phá.",
        )}
        description={l(
          "Meet ALELA's steering members, core members, doctoral researchers, master's students and research partners.",
          "Đội ngũ ALELA gồm thành viên chỉ đạo, thành viên cốt lõi, nghiên cứu sinh, học viên cao học và các thành viên đối tác.",
        )}
      />
      <section className="section">
        <div className="container">
          <div className="filter-bar">
            <SearchInput
              value={query}
              onChange={setQuery}
              placeholder={l("Search people…", "Tìm thành viên…")}
            />
            <span className="results-count" aria-live="polite">
              {filtered.length} {l("people", "thành viên")}
            </span>
          </div>
          <div
            className="filter-tabs"
            role="group"
            aria-label={l("Filter by role", "Lọc theo vai trò")}
          >
            {[{ role: 'all', label: { en: 'Everyone', vi: 'Tất cả' } },
              ...groups.filter(group => group.showWhenEmpty || data.members.some(member => matchesMemberRole(member.role, group.role)))].map((item) => (
              <button
                type="button"
                aria-pressed={role === item.role}
                className={role === item.role ? "active" : ""}
                key={item.role}
                onClick={() => setRole(item.role)}
              >
                {text(item.label)}
              </button>
            ))}
          </div>
          {visibleGroups.length ? (
            <div className="member-groups">
              {hierarchy.length + additional.length > 0 && <section className="member-research-group" aria-labelledby="member-research-heading">
                <div className="member-group-heading">
                  <h2 id="member-research-heading">{l('ALELA Research Team', 'Đội ngũ nghiên cứu ALELA')}</h2>
                  <span>{researchCount} {l('members', 'thành viên')}</span>
                </div>
                {hierarchy.length > 0 && <div className="member-hierarchy" aria-label={l('Research team hierarchy', 'Phân tầng đội ngũ nghiên cứu')}>
                  {hierarchy.map(group => renderTier(group, true))}
                </div>}
                {additional.map(group => renderTier(group, false))}
              </section>}
              {collaborators.map(group => <section className="member-collaborators" key={group.role} aria-labelledby="member-collaborators-heading">
                <div className="member-group-heading">
                  <h2 id="member-collaborators-heading">{l('Guests & Collaborators', 'Khách mời & Đối tác')}</h2>
                  <span>{group.members.length} {l('members', 'thành viên')}</span>
                </div>
                <div className="member-collaborator-grid member-tier-grid">
                  {group.members.map(item => <MemberCard item={item} key={item.id} />)}
                </div>
              </section>)}
            </div>
          ) : (
            <EmptyState
              onReset={() => {
                setQuery("");
                setRole("all");
              }}
            />
          )}
        </div>
      </section>
    </>
  );
}
export function PublicationsView() {
  const { l, data } = useLab();
  return <>
    <PageHeading eyebrow={l("Publications", "Công bố")} title={l("Knowledge, shared openly.", "Tri thức, được chia sẻ rộng mở.")} description="" showSections={false} />
    <ContentSearch kind="publications" records={data.publications} renderRecords={items => <div className="publication-results">{[...items].sort((a, b) => (b.year ?? 0) - (a.year ?? 0)).map(item => <PublicationRow item={item} key={item.id} />)}</div>} />
  </>;
}
export function PublicationRow({ item }: { item: Publication }) {
  const { locale, text, l, data } = useLab();
  return (
    <article className="publication-row">
      <span className="publication-year">{item.year}</span>
      <div className="publication-content">
        <div className="publication-meta">
          <span className="badge">{l(item.type, roleVi[item.type])}</span>
          <span className="label">
            {text(
              data.research.find((a) => a.id === item.area)?.title || {
                en: "",
                vi: "",
              },
            )}
          </span>
        </div>
        <Link href={`/${locale}/publications/${item.id}`}>
          <h3>
            {text(item.title)}
            <ArrowUpRight size={17} />
          </h3>
        </Link>
        <p className="publication-authors">
          {item.authors
            .map((id) => {
              const member = data.members.find((m) => m.id === id);
              return member ? (
                <Link key={id} href={`/${locale}/people/${id}`}>
                  {text(member.title)}
                </Link>
              ) : (
                <span key={id}>
                  {id}
                </span>
              );
            })
            .reduce<React.ReactNode[]>(
              (acc, item, index) => [...acc, ...(index ? [", "] : []), item],
              [],
            )}
        </p>
        <p className="publication-venue">{text(item.venue)}</p>
        <PublicationResources item={item} />
      </div>
    </article>
  );
}
export function PublicationResources({ item }: { item: Publication }) {
  const { l, text, data, locale } = useLab();
  const [resource, setResource] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);
  const bibtex = `@${item.type === "Journal" ? "article" : item.type === "Book" ? "book" : item.type === "Conference" ? "inproceedings" : "misc"}{publication_${item.id.replace(/-/g, "_")},\n  title = {${text(item.title)}},\n  author = {${item.authors
    .map((id) => {
      const member = data.members.find((m) => m.id === id);
      return member ? text(member.title) : id;
    })
    .join(
      " and ",
    )}},\n  year = {${item.year ?? ""}}\n}`;
  async function copy() {
    try {
      await navigator.clipboard.writeText(bibtex);
      setCopied(true);
      setCopyError(false);
    } catch {
      setCopyError(true);
    }
  }
  return (
    <>
      <div className="resource-links">
        {[...item.resources, "BibTeX"].map((name) => {
          const Icon =
            name === "PDF"
              ? FileText
              : name === "Code"
                ? Code2
                : name === "BibTeX"
                  ? Quote
                  : Link2;
          return (
            <button
              key={name}
              onClick={() => {
                const link = item.links?.[name as 'DOI' | 'PDF' | 'Code'];
                if (link && /^https?:\/\//i.test(link)) {
                  window.open(link, '_blank', 'noopener,noreferrer');
                  return;
                }
                setResource(name);
                setCopied(false);
                setCopyError(false);
              }}
            >
              <Icon size={13} />
              {name}
            </button>
          );
        })}
      </div>
      <Dialog
        open={resource !== null}
        onOpenChange={(open) => !open && setResource(null)}
        title={
          resource === "BibTeX"
            ? l("Citation preview", "Xem trích dẫn")
            : resource ?? ''
        }
        description={resource === 'BibTeX' ? undefined : l('The link for this resource has not been supplied.', 'Liên kết tài nguyên này chưa được cập nhật.')}
      >
        {resource === "BibTeX" ? (
          <>
            <pre className="bibtex">{bibtex}</pre>
            <Button onClick={copy} variant="outline">
              {copied ? <Check size={16} /> : <Copy size={16} />}{" "}
              {copied
                ? l("Copied", "Đã sao chép")
                : l("Copy BibTeX", "Sao chép BibTeX")}
            </Button>
            {copyError && (
              <p role="alert">
                {l(
                  "Clipboard unavailable. Select and copy the text above.",
                  "Không thể truy cập bộ nhớ tạm. Hãy chọn và sao chép văn bản trên.",
                )}
              </p>
            )}
          </>
        ) : (
          <div className="resource-preview">
            <FileText size={36} />
            <h3>{text(item.title)}</h3>
            <p>
              {l(
                "Resource links are reserved for verified research outputs when a backend is connected.",
                "Liên kết tài nguyên dành cho kết quả nghiên cứu đã xác minh khi kết nối backend.",
              )}
            </p>
            <Link
              className="text-link"
              href={`/${locale}/publications/${item.id}`}
              onClick={() => setResource(null)}
            >
              {l("View publication details", "Xem chi tiết công bố")}
              <ArrowRightIcon />
            </Link>
          </div>
        )}
      </Dialog>
    </>
  );
}
function ArrowRightIcon() {
  return <ArrowUpRight size={16} />;
}
export function ProjectsView() {
  const { l, data } = useLab();
  return <>
    <PageHeading eyebrow={l("Projects", "Đề tài")} title={l("Ideas with a direction.", "Ý tưởng có định hướng.")} description="" showSections={false} />
    <ContentSearch kind="projects" records={data.projects} renderRecords={items => <div className="cards-grid three">{items.map(item => <ProjectCard item={item} key={item.id} />)}</div>} />
  </>;
}
export function NewsView() {
  const { l, data } = useLab();
  return <>
    <PageHeading eyebrow={l("News & events", "Tin & sự kiện")} title={l("A laboratory, always in motion.", "Phòng lab luôn chuyển động.")} description="" showSections={false} />
    <ContentSearch kind="news" records={data.news} renderRecords={items => <div className="cards-grid three">{[...items].sort((a, b) => b.date.localeCompare(a.date)).map(item => <NewsCard item={item} key={item.id} />)}</div>} />
  </>;
}
