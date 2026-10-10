import Image from "../site-image";
import { formatPublicDate } from "../../lib/utils";
import Link from "../site-link";
import {
  ArrowLeft,
  ArrowUpRight,
  Check,
  CalendarDays,
  MapPin,
  Mail,
  BookOpen,
} from "lucide-react";
import { roleVi, useLab } from "../lab-provider";
import {
  SectionHeading,
  ProjectCard,
  PublicationPreview,
  DemoNotice,
  MemberCard,
} from "../shared";
import { PublicationResources } from "./explore";
import { Button } from "../ui/button";
import { RichTextBody } from '../rich-text-body';
import { ArticleSourceDetails } from '../article-source-details';
export function DetailView({ section, id }: { section: string; id: string }) {
  const { data, text, locale, l, profile } = useLab();
  const key =
    section === "people"
      ? "members"
      : (section as "research" | "publications" | "projects" | "news");
  const item = data[key]?.find((item) => item.id === id);
  if (!item)
    return (
      <section className="section">
        <div className="container missing-record">
          <BookOpen size={40} />
          <h1>
            {l(
              "This record is no longer available.",
              "Bản ghi này không còn khả dụng.",
            )}
          </h1>
          <p>
            {l(
              "This record has not been published or is no longer visible.",
              "Bản ghi chưa được xuất bản hoặc đã được ẩn.",
            )}
          </p>
          <Button asChild>
            <Link href={`/${locale}/${section}`}>
              {l("Back to collection", "Về danh sách")}
            </Link>
          </Button>
        </div>
      </section>
    );
  const relatedPublications = data.publications.filter((p) =>
    section === "research"
      ? p.area === id
      : section === "projects"
        ? p.project === id
        : section === "people"
          ? p.authors.includes(id)
          : false,
  );
  return (
    <>
      <section className="detail-heading">
        <div className="container">
          <Link className="back-link" href={`/${locale}/${section}`}>
            <ArrowLeft size={16} />
            {l("Back to", "Quay lại")}{" "}
            {l(
              section === "people" ? "people" : section,
              {
                research: "nghiên cứu",
                people: "thành viên",
                projects: "dự án",
                publications: "công bố",
                news: "tin tức",
              }[section] || section,
            )}
          </Link>
          <div className="detail-heading-grid">
            <div>
              <span className="eyebrow">
                <span />
                {profile.showDemoNotice && l("DEMONSTRATION RECORD", "BẢN GHI MINH HỌA")}
              </span>
              <h1>{text(item.title)}</h1>
              <p>{text(item.description)}</p>
            </div>
            {"image" in item && !('initials' in item) && (
              <div className="detail-image">
                <Image
                  src={item.image ?? ''}
                  alt=""
                  fill
                  loading="eager"
                  fetchPriority="high"
                  sizes="(max-width: 800px) 100vw, 45vw"
                />
              </div>
            )}
            {"initials" in item && (
              <div
                className="profile-portrait"
                style={{ background: item.color }}
              >
                {item.image ? <Image src={item.image} alt={text(item.title)} fill sizes="45vw" /> : <><span className="avatar-grid" /><span>{item.initials}</span></>}
                {profile.showDemoNotice && <small>{l("ILLUSTRATIVE PROFILE", "HỒ SƠ MINH HỌA")}</small>}
              </div>
            )}
          </div>
        </div>
      </section>
      <section className="section">
        <div className="container">
          <DemoNotice />
          {section === "research" && "topics" in item && (
            <>
              <div className="detail-body-grid">
                <div>
                  <h2>
                    {l("Our research questions", "Các câu hỏi nghiên cứu")}
                  </h2>
                  <p>{text(item.description)}</p>
                  <ul className="topic-list">
                    {item.topics.map((topic, i) => (
                      <li key={i}>
                        <Check size={18} />
                        {text(topic)}
                      </li>
                    ))}
                  </ul>
                </div>
                <aside className="detail-aside">
                  <span className="label">{l("GET INVOLVED", "THAM GIA")}</span>
                  <h3>
                    {l("Ask the next question.", "Đặt câu hỏi tiếp theo.")}
                  </h3>
                  <p>
                    {l(
                      "Explore an example research pathway and share your interests.",
                      "Khám phá con đường nghiên cứu mẫu và chia sẻ mối quan tâm.",
                    )}
                  </p>
                  <Button asChild>
                    <Link href={`/${locale}/join`}>
                      {l("Research opportunities", "Cơ hội nghiên cứu")}
                      <ArrowUpRight size={16} />
                    </Link>
                  </Button>
                </aside>
              </div>
              <div className="detail-related">
                <SectionHeading
                  eyebrow={l("CONNECTED WORK", "CÔNG VIỆC LIÊN QUAN")}
                  title={l("Related projects", "Đề tài liên quan")}
                />
                <div className="cards-grid three">
                  {data.projects
                    .filter((p) => p.area === id)
                    .map((p) => (
                      <ProjectCard key={p.id} item={p} />
                    ))}
                </div>
              </div>
              <div className="detail-related">
                <SectionHeading
                  eyebrow={l("THE TEAM", "NHÓM NGHIÊN CỨU")}
                  title={l(
                    "People in this direction",
                    "Thành viên theo hướng này",
                  )}
                />
                <div className="cards-grid four">
                  {data.members
                    .filter((p) => p.area === id)
                    .map((p) => (
                      <MemberCard key={p.id} item={p} />
                    ))}
                </div>
              </div>
            </>
          )}
          {section === "people" && "role" in item && (
            <div className="detail-body-grid">
              <div>
                <span className="badge">{l(item.role, roleVi[item.role])}</span>
                <h2>
                  {l(
                    "Research interests & biography",
                    "Mối quan tâm nghiên cứu & tiểu sử",
                  )}
                </h2>
                <p>{text(item.description)}</p>
                {data.research
                  .filter((a) => a.id === item.area)
                  .map((a) => (
                    <Link
                      key={a.id}
                      href={`/${locale}/research/${a.id}`}
                      className="text-link"
                    >
                      {text(a.title)}
                      <ArrowUpRight size={16} />
                    </Link>
                  ))}
              </div>
              <aside className="detail-aside">
                <Mail size={22} />
                <h3>{l("Contact & collaboration", "Liên hệ & hợp tác")}</h3>
                <p>{item.email}</p>
                <Link href={`/${locale}/contact`} className="text-link">
                  {l("Contact the laboratory", "Liên hệ phòng lab")}
                  <ArrowUpRight size={17} />
                </Link>
              </aside>
            </div>
          )}
          {section === "projects" && "objectives" in item && (
            <div className="detail-body-grid">
              <div>
                <h2>{l("Project overview", "Tổng quan dự án")}</h2>
                <p>{text(item.description)}</p>
                {profile.showDemoNotice && <p>
                  {l(
                    "This is an illustrative project concept. Timelines and objectives are examples; no funding, partnership, deployment, or research result is claimed.",
                    "Đây là ý tưởng dự án minh họa. Thời gian và mục tiêu là ví dụ; không tuyên bố tài trợ, đối tác, triển khai hay kết quả nghiên cứu.",
                  )}
                </p>}
                <RichTextBody html={item.bodyHtml} paragraphs={[]} />
                <h3>{l("Research objectives", "Mục tiêu nghiên cứu")}</h3>
                <ul className="topic-list">
                  {item.objectives.map((o, i) => (
                    <li key={i}>
                      <Check size={18} />
                      {text(o)}
                    </li>
                  ))}
                </ul>
              </div>
              <aside className="detail-aside">
                <span className="label">
                  {l("PROJECT SNAPSHOT", "TỔNG QUAN DỰ ÁN")}
                </span>
                <dl>
                  <dt>{l("Status", "Trạng thái")}</dt>
                  <dd>
                    <span className="badge">
                      {l(item.status, roleVi[item.status])}
                    </span>
                  </dd>
                  <dt>{l("Period", "Thời gian")}</dt>
                  <dd>{item.period}</dd>
                  <dt>{l("Category", "Nhóm")}</dt>
                  <dd>{text(item.category)}</dd>
                  <dt>{l("Project lead", "Trưởng dự án")}</dt>
                  <dd>
                    {data.members
                      .filter((m) => m.id === item.lead)
                      .map((m) => (
                        <Link key={m.id} href={`/${locale}/people/${m.id}`}>
                          {text(m.title)}
                          <ArrowUpRight size={13} />
                        </Link>
                      ))}
                  </dd>
                </dl>
                <ArticleSourceDetails value={item} />
              </aside>
            </div>
          )}
          {section === "publications" && "authors" in item && (
            <div className="detail-body-grid">
              <div>
                <span className="badge">
                  {item.year} · {l(item.type, roleVi[item.type])}
                </span>
                <h2>{l("Abstract", "Tóm tắt")}</h2>
                <p>{text(item.description)}</p>
                {profile.showDemoNotice && <p>
                  {l(
                    "This record demonstrates publication metadata and resource controls. It does not describe a published study, peer review, or verified result. All citations generated from this page are marked as fictional.",
                    "Bản ghi minh họa metadata và các nút tài nguyên. Không mô tả nghiên cứu đã công bố, bình duyệt hay kết quả xác minh. Trích dẫn tạo từ trang này được đánh dấu giả định.",
                  )}
                </p>}
                <RichTextBody html={item.bodyHtml} paragraphs={[]} />
                <h3>{l("Contributors", "Tác giả")}</h3>
                <div className="contributor-links">
                  {item.authors.map((id) => {
                    const member = data.members.find((m) => m.id === id);
                    return member ? (
                      <Link key={id} href={`/${locale}/people/${id}`}>
                        {text(member.title)}
                        <ArrowUpRight size={15} />
                      </Link>
                    ) : (
                      <span key={id}>
                        {id}
                      </span>
                    );
                  })}
                </div>
                <h3>{l("Resources & citation", "Tài nguyên & trích dẫn")}</h3>
                <PublicationResources item={item} />
              </div>
              <aside className="detail-aside">
                <span className="label">
                  {l("PUBLICATION DETAILS", "CHI TIẾT CÔNG BỐ")}
                </span>
                <dl>
                  <dt>{l("Year", "Năm")}</dt>
                  <dd>{item.year}</dd>
                  <dt>{l("Type", "Loại")}</dt>
                  <dd>{l(item.type, roleVi[item.type])}</dd>
                  <dt>{l("Venue", "Nơi công bố")}</dt>
                  <dd>{text(item.venue)}</dd>
                </dl>
                <ArticleSourceDetails value={item} />
                {data.projects
                  .filter((p) => p.id === item.project)
                  .map((p) => (
                    <Link
                      key={p.id}
                      href={`/${locale}/projects/${p.id}`}
                      className="text-link"
                    >
                      {text(p.title)}
                      <ArrowUpRight size={16} />
                    </Link>
                  ))}
              </aside>
            </div>
          )}
          {section === "news" && "body" in item && (
            <div className="detail-body-grid">
              <article className="article-body">
                <div className="news-meta">
                  <span className="badge">
                    {l(item.category, roleVi[item.category])}
                  </span>
                  <time dateTime={item.date}>
                    {formatPublicDate(item.date, locale)}
                  </time>
                </div>
                <RichTextBody html={item.bodyHtml} paragraphs={item.body} />
                <h2>{l("Continue the conversation", "Tiếp tục trao đổi")}</h2>
                <p>
                  {l(
                    "Explore the laboratory’s research directions and sample opportunities to learn how you could participate in a real research community.",
                    "Khám phá hướng nghiên cứu và cơ hội mẫu để tìm hiểu cách tham gia cộng đồng nghiên cứu thực tế.",
                  )}
                </p>
                <Button asChild>
                  <Link href={`/${locale}/join`}>
                    {l("Explore opportunities", "Khám phá cơ hội")}
                    <ArrowUpRight size={16} />
                  </Link>
                </Button>
              </article>
              <aside className="detail-aside">
                <span className="label">
                  {l("STORY DETAILS", "CHI TIẾT BÀI VIẾT")}
                </span>
                {item.date && <p className="icon-line">
                  <CalendarDays size={18} />
                  {item.date}
                </p>}
                {text(item.location) && <p className="icon-line">
                  <MapPin size={18} />
                  {text(item.location)}
                </p>}
                <ArticleSourceDetails value={item} />
                {profile.showDemoNotice && <p>
                  {l(
                    "Sample event or story. No registration or actual attendance is available.",
                    "Sự kiện hoặc bài viết mẫu. Không có đăng ký hay tham dự thực tế.",
                  )}
                </p>}
              </aside>
            </div>
          )}
          {relatedPublications.length > 0 && (
            <div className="detail-related">
              <SectionHeading
                eyebrow={l("RESEARCH OUTPUTS", "KẾT QUẢ NGHIÊN CỨU")}
                title={l("Related publications", "Công bố liên quan")}
              />
              <div className="publication-list">
                {relatedPublications.map((p) => (
                  <PublicationPreview key={p.id} item={p} />
                ))}
              </div>
            </div>
          )}
        </div>
      </section>
    </>
  );
}
