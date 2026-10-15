import Image from "./site-image";
import { formatPublicDate } from "../lib/utils";
import Link from "./site-link";
import {
  ArrowUpRight,
  ArrowRight,
  Globe2,
  Cpu,
  Radio,
  Network,
  SearchX,
  FileText,
} from "lucide-react";
import { roleVi, useLab } from "./lab-provider";
import type {
  Member,
  NewsItem,
  Project,
  ResearchArea,
  Publication,
} from "../lib/types";
import { DynamicSections } from './dynamic-sections';
export function SectionHeading({
  eyebrow,
  title,
  description,
  href,
  linkLabel,
}: {
  eyebrow: string;
  title: string;
  description?: string;
  href?: string;
  linkLabel?: string;
}) {
  return (
    <div className="section-heading">
      <div>
        <span className="eyebrow">
          <span />
          {eyebrow}
        </span>
        <h2>{title}</h2>
        {description && <p>{description}</p>}
      </div>
      {href && (
        <Link href={href} className="text-link">
          {linkLabel}
          <ArrowUpRight size={17} />
        </Link>
      )}
    </div>
  );
}
export function PageHeading({
  eyebrow,
  title,
  description,
  usePageContent = true,
}: {
  eyebrow: string;
  title: string;
  description: string;
  usePageContent?: boolean;
}) {
  const { locale, l, page, text } = useLab();
  const headingDescription = usePageContent && page ? text(page.description) : description;
  return (
    <><section className="page-heading">
      <div className="container">
        <div className="breadcrumb">
          <Link href={`/${locale}`}>{l("Home", "Trang chủ")}</Link>
          <span>/</span>
          <span>{page ? text(page.navigationTitle) || text(page.title) : eyebrow}</span>
        </div>
        <span className="eyebrow">
          <span />
          {page ? text(page.navigationTitle) || text(page.title) : eyebrow}
        </span>
        <h1>{usePageContent && page ? text(page.title) : title}</h1>
        {headingDescription && <p>{headingDescription}</p>}
        <span className="page-heading-mark" aria-hidden="true">
          +
        </span>
      </div>
    </section><DynamicSections /></>
  );
}
const icons = { earth: Globe2, cpu: Cpu, radio: Radio, network: Network };
export function ResearchCard({
  item,
  index = 0,
}: {
  item: ResearchArea;
  index?: number;
}) {
  const { locale, text, l } = useLab();
  const Icon = icons[item.icon];
  return (
    <Link className="research-card" href={`/${locale}/research/${item.id}`}>
      <div className="card-image">
        <Image
          src={item.image}
          alt={item.imageAlt?.[locale] ?? ''}
          fill
          sizes="(max-width: 640px) 100vw, (max-width: 1000px) 50vw, 33vw"
        />
        <span className="image-index">0{index + 1}</span>
        <span className="image-arrow">
          <ArrowUpRight size={19} />
        </span>
      </div>
      <div className="card-body">
        <span className="research-icon">
          <Icon size={22} />
        </span>
        <h3>{text(item.title)}</h3>
        <p>{text(item.description)}</p>
        <span className="card-more">
          {l("Explore research", "Khám phá nghiên cứu")}
          <ArrowRight size={15} />
        </span>
      </div>
    </Link>
  );
}
export function MemberCard({ item }: { item: Member }) {
  const { locale, text, l } = useLab();
  return (
    <Link className={`member-card${item.scholarProfile ? ' member-card-scholar' : ''}`} href={`/${locale}/people/${item.id}`}>
      <div className="member-avatar" style={{ background: item.color }}>
        {item.image ? <Image src={item.image} alt={text(item.title)} fill sizes="(max-width: 640px) 50vw, 25vw" /> : <><span className="avatar-grid" /><span>{item.initials}</span></>}
        <ArrowUpRight size={19} />
      </div>
      <span className="label">{l(item.role, roleVi[item.role])}</span>
      <h3>{text(item.title)}</h3>
      <p>
        {text(item.description)}
      </p>
      <span className="card-more">
        {l("View profile", "Xem hồ sơ")}
        <ArrowRight size={16} aria-hidden="true" />
      </span>
    </Link>
  );
}
export function ProjectCard({ item }: { item: Project }) {
  const { locale, text, l } = useLab();
  return (
    <Link href={`/${locale}/projects/${item.id}`} className="project-card">
      <div className="card-image">
        <Image
          src={item.image}
          alt={item.imageAlt?.[locale] ?? ''}
          fill
          sizes="(max-width: 640px) 100vw, 33vw"
        />
        <span
          className={`status-badge ${item.status === "Completed" ? "completed" : ""}`}
        >
          <span />
          {l(item.status, roleVi[item.status])}
        </span>
        <span className="image-arrow">
          <ArrowUpRight size={19} />
        </span>
      </div>
      <div className="card-body">
        <span className="label">
          {text(item.category)} <span className="mid-dot">·</span> {item.period}
        </span>
        <h3>{text(item.title)}</h3>
        <p>{text(item.description)}</p>
      </div>
    </Link>
  );
}
export function NewsCard({ item }: { item: NewsItem }) {
  const { locale, text, l } = useLab();
  return (
    <Link href={`/${locale}/news/${item.id}`} className="news-card">
      <div className="card-image">
        <Image
          src={item.image}
          alt={item.imageAlt?.[locale] ?? ''}
          fill
          sizes="(max-width: 640px) 100vw, 33vw"
        />
        <span className="image-arrow">
          <ArrowUpRight size={19} />
        </span>
      </div>
      <div className="card-body">
        <div className="news-meta">
          <span className="label">
            {l(item.category, roleVi[item.category])}
          </span>
          <time dateTime={item.date}>
            {formatPublicDate(item.date, locale)}
          </time>
        </div>
        <h3>{text(item.title)}</h3>
        <p>{text(item.description)}</p>
      </div>
    </Link>
  );
}
export function PublicationPreview({ item }: { item: Publication }) {
  const { data, text, l, locale } = useLab();
  return (
    <Link
      href={`/${locale}/publications/${item.id}`}
      className="publication-preview"
    >
      <span className="publication-icon">
        <FileText size={23} />
      </span>
      <div>
        <div className="publication-meta">
          <span>{item.year}</span>
          <span className="label">{l(item.type, roleVi[item.type])}</span>
        </div>
        <h3>{text(item.title)}</h3>
        <p>
          {item.authors
            .map((id) => {
              const member = data.members.find((m) => m.id === id);
              return member
                ? text(member.title)
                : id;
            })
            .join(", ")}
          <span className="mid-dot">·</span>
          {text(item.venue)}
        </p>
      </div>
      <ArrowUpRight size={21} />
    </Link>
  );
}
export function EmptyState({ onReset }: { onReset?: () => void }) {
  const { l } = useLab();
  return (
    <div className="empty-state" role="status">
      <SearchX size={35} />
      <h3>{l("No matching results", "Không có kết quả phù hợp")}</h3>
      <p>
        {l(
          "Try another search or clear your filters.",
          "Thử tìm kiếm khác hoặc xóa bộ lọc.",
        )}
      </p>
      {onReset && (
        <button onClick={onReset} className="text-link">
          {l("Clear all filters", "Xóa tất cả bộ lọc")}
          <ArrowRight size={16} />
        </button>
      )}
    </div>
  );
}
