import { useState } from "react";
import {
  Search,
  SlidersHorizontal,
  X,
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
  DemoNotice,
} from "../shared";
import { Select } from "../ui/select";
import { Button } from "../ui/button";
import { Dialog } from "../ui/dialog";
import type { Publication } from "../../lib/types";
import { memberRoleGroups } from '../../../member-roles';
export function ResearchView() {
  const { l, data } = useLab();
  return (
    <>
      <PageHeading
        eyebrow={l("Research", "Nghiên cứu")}
        title={l(
          "Questions that connect us.",
          "Những câu hỏi kết nối chúng ta.",
        )}
        description={l(
          "Four complementary directions. One shared drive to understand and improve our world.",
          "Bốn hướng nghiên cứu bổ trợ. Một động lực chung để hiểu và cải thiện thế giới.",
        )}
      />
      <section className="section">
        <div className="container">
          <DemoNotice />
          <div className="cards-grid two">
            {data.research.map((item, index) => (
              <ResearchCard key={item.id} item={item} index={index} />
            ))}
          </div>
        </div>
      </section>
    </>
  );
}
export function PeopleView() {
  const { l, text, data } = useLab();
  const [role, setRole] = useState("all");
  const [query, setQuery] = useState("");
  const isGrouped = (memberRole: string) => memberRoleGroups.some(group => group.role === memberRole);
  const otherGroup = {
    role: 'other', label: { en: 'Other members', vi: 'Thành viên khác' },
    title: { en: 'Other Members', vi: 'Thành viên khác' },
  };
  const groups = [
    ...memberRoleGroups.filter(group => group.role !== 'Guest'),
    otherGroup,
    ...memberRoleGroups.filter(group => group.role === 'Guest'),
  ];
  const matchesRole = (memberRole: string, selected: string) => selected === 'other'
    ? !isGrouped(memberRole) : memberRole === selected;
  const filtered = data.members.filter(
    (item) =>
      (role === "all" || matchesRole(item.role, role)) &&
      `${text(item.title)} ${text(item.description)}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  return (
    <>
      <PageHeading
        eyebrow={l("People", "Thành viên")}
        title={l(
          "Many minds. One shared curiosity.",
          "Nhiều tư duy. Chung niềm khám phá.",
        )}
        description={l(
          "Meet ALELA's steering members, core members, doctoral researchers and research partners.",
          "Đội ngũ ALELA gồm thành viên chỉ đạo, thành viên cốt lõi, nghiên cứu sinh và các thành viên đối tác.",
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
              ...groups.filter(group => data.members.some(member => matchesRole(member.role, group.role)))].map((item) => (
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
          {filtered.length ? (
            <div className="member-hierarchy" aria-label={l('Laboratory membership hierarchy', 'Phân tầng thành viên lab')}>
              {groups.map(group => {
                const members = filtered.filter(item => matchesRole(item.role, group.role));
                if (!members.length) return null;
                const headingId = `member-group-${group.role.toLowerCase().replace(/ /g, '-')}`;
                return <section className="member-tier" key={group.role} aria-labelledby={headingId}>
                  <div className="member-tier-heading">
                    <h2 id={headingId}>{text(group.title)}</h2>
                    <span>{members.length} {l('members', 'thành viên')}</span>
                  </div>
                  <div className={`member-tier-grid member-tier-grid-${Math.min(members.length, 6)}`}>
                    {members.map(item => <MemberCard item={item} key={item.id} />)}
                  </div>
                </section>;
              })}
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
export function SearchInput({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
}) {
  return (
    <div className="search-field">
      <Search size={18} />
      <input
        type="search"
        aria-label={placeholder}
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
      {value && (
        <button
          onClick={() => onChange("")}
          aria-label={
            placeholder.startsWith("Search") ? "Clear search" : "Xóa tìm kiếm"
          }
        >
          <X size={15} />
        </button>
      )}
    </div>
  );
}
export function PublicationsView() {
  const { l, text, data } = useLab();
  const [query, setQuery] = useState("");
  const [year, setYear] = useState("all");
  const [type, setType] = useState("all");
  const [author, setAuthor] = useState("all");
  const [area, setArea] = useState("all");
  const reset = () => {
    setQuery("");
    setYear("all");
    setType("all");
    setAuthor("all");
    setArea("all");
  };
  const filtered = data.publications.filter(
    (item) =>
      (year === "all" || String(item.year) === year) &&
      (type === "all" || item.type === type) &&
      (author === "all" || item.authors.includes(author)) &&
      (area === "all" || item.area === area) &&
      `${text(item.title)} ${text(item.venue)} ${item.authors
        .map((id) => {
          const m = data.members.find((x) => x.id === id);
          return m ? text(m.title) : "";
        })
        .join(" ")}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  return (
    <>
      <PageHeading
        eyebrow={l("Publications", "Công bố")}
        title={l(
          "Knowledge, shared openly.",
          "Tri thức, được chia sẻ rộng mở.",
        )}
        description={l(
          "Explore our sample research library. Filter by year, format, contributor, or research direction.",
          "Khám phá thư viện nghiên cứu mẫu. Lọc theo năm, loại, tác giả hoặc hướng nghiên cứu.",
        )}
      />
      <section className="section">
        <div className="container">
          <DemoNotice />
          <div className="filter-panel">
            <SearchInput
              value={query}
              onChange={setQuery}
              placeholder={l(
                "Search titles, authors, or keywords…",
                "Tìm tiêu đề, tác giả hoặc từ khóa…",
              )}
            />
            <div className="filter-selects">
              <SlidersHorizontal size={18} />
              <Select
                label={l("Year", "Năm")}
                value={year}
                onValueChange={setYear}
                options={[
                  { value: "all", label: l("All years", "Tất cả năm") },
                  ...Array.from(new Set(data.publications.map((p) => p.year)))
                    .filter((value): value is number => value !== null)
                    .sort((a, b) => b - a)
                    .map((y) => ({ value: String(y), label: String(y) })),
                ]}
              />
              <Select
                label={l("Type", "Loại")}
                value={type}
                onValueChange={setType}
                options={[
                  { value: "all", label: l("All types", "Tất cả loại") },
                  ...["Journal", "Conference", "Book", "Preprint"].map((v) => ({
                    value: v,
                    label: l(v, roleVi[v]),
                  })),
                ]}
              />
              <Select
                label={l("Author", "Tác giả")}
                value={author}
                onValueChange={setAuthor}
                options={[
                  { value: "all", label: l("All authors", "Tất cả tác giả") },
                  ...data.members.map((m) => ({
                    value: m.id,
                    label: text(m.title),
                  })),
                ]}
              />
              <Select
                label={l("Research area", "Lĩnh vực")}
                value={area}
                onValueChange={setArea}
                options={[
                  {
                    value: "all",
                    label: l("All research areas", "Tất cả lĩnh vực"),
                  },
                  ...data.research.map((a) => ({
                    value: a.id,
                    label: text(a.title),
                  })),
                ]}
              />
              <button onClick={reset} className="clear-filters">
                {l("Reset", "Đặt lại")}
              </button>
            </div>
          </div>
          <div className="results-header" aria-live="polite">
            <span>
              {l("Showing", "Hiển thị")} <strong>{filtered.length}</strong>{" "}
              {l("publications", "công bố")}
            </span>
            <span>{l("Newest first", "Mới nhất trước")}</span>
          </div>
          {filtered.length ? (
            <div className="publication-results">
              {[...filtered]
                .sort((a, b) => (b.year ?? 0) - (a.year ?? 0))
                .map((item) => (
                  <PublicationRow item={item} key={item.id} />
                ))}
            </div>
          ) : (
            <EmptyState onReset={reset} />
          )}
        </div>
      </section>
    </>
  );
}
export function PublicationRow({ item }: { item: Publication }) {
  const { locale, text, l, data, profile } = useLab();
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
          {profile.showDemoNotice && <span className="sample-label">{l("Demo", "Mẫu")}</span>}
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
  const { l, text, data, locale, profile } = useLab();
  const [resource, setResource] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);
  const bibtex = `@${item.type === "Journal" ? "article" : item.type === "Book" ? "book" : item.type === "Conference" ? "inproceedings" : "misc"}{${profile.showDemoNotice ? "nexus_demo" : "publication"}_${item.id.replace(/-/g, "_")},\n  title = {${text(item.title)}},\n  author = {${item.authors
    .map((id) => {
      const member = data.members.find((m) => m.id === id);
      return member ? text(member.title) : id;
    })
    .join(
      " and ",
    )}},\n  year = {${item.year ?? ""}}${profile.showDemoNotice ? ",\n  note = {FICTIONAL DEMONSTRATION RECORD - not a published work}" : ""}\n}`;
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
            : l(`${resource} · sample resource`, `${resource} · tài nguyên mẫu`)
        }
        description={profile.showDemoNotice ? l(
          "This publication is fictional. No real DOI, paper, or code repository is associated with it.",
          "Công bố này là giả định. Không có DOI, bài báo hay kho mã thực tế liên quan.",
        ) : l('The link for this resource has not been supplied.', 'Liên kết tài nguyên này chưa được cập nhật.')}
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
  const { l, text, data } = useLab();
  const [status, setStatus] = useState("all");
  const [area, setArea] = useState("all");
  const [query, setQuery] = useState("");
  const filtered = data.projects.filter(
    (item) =>
      (status === "all" || item.status === status) &&
      (area === "all" || item.area === area) &&
      `${text(item.title)} ${text(item.description)}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  return (
    <>
      <PageHeading
        eyebrow={l("Projects", "Đề tài")}
        title={l("Ideas with a direction.", "Ý tưởng có định hướng.")}
        description={l(
          "Explore demonstration projects connecting foundational questions with practical research concepts.",
          "Khám phá các dự án minh họa kết nối câu hỏi nền tảng với ý tưởng nghiên cứu thực tiễn.",
        )}
      />
      <section className="section">
        <div className="container">
          <div className="filter-bar">
            <SearchInput
              value={query}
              onChange={setQuery}
              placeholder={l("Search projects…", "Tìm dự án…")}
            />
            <Select
              label={l("Research category", "Nhóm nghiên cứu")}
              value={area}
              onValueChange={setArea}
              options={[
                { value: "all", label: l("All categories", "Tất cả nhóm") },
                ...data.research.map((a) => ({
                  value: a.id,
                  label: text(a.title),
                })),
              ]}
            />
          </div>
          <div
            className="filter-tabs"
            role="group"
            aria-label={l("Project status", "Trạng thái dự án")}
          >
            {["all", "Ongoing", "Completed"].map((item) => (
              <button
                key={item}
                aria-pressed={status === item}
                className={status === item ? "active" : ""}
                onClick={() => setStatus(item)}
              >
                {item === "all"
                  ? l("All projects", "Tất cả dự án")
                  : l(item, roleVi[item])}
              </button>
            ))}
            <span className="results-count" aria-live="polite">
              {filtered.length} {l("projects", "dự án")}
            </span>
          </div>
          {filtered.length ? (
            <div className="cards-grid three">
              {filtered.map((item) => (
                <ProjectCard item={item} key={item.id} />
              ))}
            </div>
          ) : (
            <EmptyState
              onReset={() => {
                setQuery("");
                setStatus("all");
                setArea("all");
              }}
            />
          )}
        </div>
      </section>
    </>
  );
}
export function NewsView() {
  const { l, text, data } = useLab();
  const [category, setCategory] = useState("all");
  const [query, setQuery] = useState("");
  const filtered = data.news.filter(
    (item) =>
      (category === "all" || item.category === category) &&
      `${text(item.title)} ${text(item.description)}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  return (
    <>
      <PageHeading
        eyebrow={l("News & events", "Tin & sự kiện")}
        title={l(
          "A laboratory, always in motion.",
          "Phòng lab luôn chuyển động.",
        )}
        description={l(
          "Research conversations, new ideas, and moments from our illustrative community. All events are examples.",
          "Trao đổi nghiên cứu, ý tưởng mới và khoảnh khắc từ cộng đồng minh họa. Mọi sự kiện đều là ví dụ.",
        )}
      />
      <section className="section">
        <div className="container">
          <div className="filter-bar">
            <SearchInput
              value={query}
              onChange={setQuery}
              placeholder={l("Search news and events…", "Tìm tin và sự kiện…")}
            />
            <span className="results-count" aria-live="polite">
              {filtered.length} {l("stories", "bài viết")}
            </span>
          </div>
          <div
            className="filter-tabs"
            role="group"
            aria-label={l("News category", "Nhóm tin tức")}
          >
            {["all", "News", "Seminar", "Achievement", "Event"].map((item) => (
              <button
                key={item}
                aria-pressed={category === item}
                className={category === item ? "active" : ""}
                onClick={() => setCategory(item)}
              >
                {item === "all"
                  ? l("All updates", "Tất cả tin")
                  : l(item, roleVi[item])}
              </button>
            ))}
          </div>
          {filtered.length ? (
            <div className="cards-grid three">
              {[...filtered]
                .sort((a, b) => b.date.localeCompare(a.date))
                .map((item) => (
                  <NewsCard item={item} key={item.id} />
                ))}
            </div>
          ) : (
            <EmptyState
              onReset={() => {
                setQuery("");
                setCategory("all");
              }}
            />
          )}
        </div>
      </section>
    </>
  );
}
