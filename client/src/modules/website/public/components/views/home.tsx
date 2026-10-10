import Image from "../site-image";
import Link from "../site-link";
import {
  ArrowUpRight,
  ArrowRight,
  MoveDown,
  Globe2,
  Network,
  Cpu,
  Layers,
  Plus,
} from "lucide-react";
import { useLab } from "../lab-provider";
import { Button } from "../ui/button";
import { DynamicSections } from '../dynamic-sections';
import {
  SectionHeading,
  ResearchCard,
  PublicationPreview,
  ProjectCard,
  NewsCard,
  MemberCard,
} from "../shared";
export function HomeView() {
  const { locale, l, data, profile, page, text } = useLab();
  const heading = (page ? text(page.title) : `${l('Intelligent systems.', 'Hệ thống thông minh.')}\n${l('Meaningful impact.', 'Tác động thiết thực.')}`).split('\n');
  return (
    <>
      <section className="hero">
        <Image
          src="/images/earth.jpg"
          alt={l(
            "Earth at night viewed from orbit",
            "Trái Đất về đêm nhìn từ quỹ đạo",
          )}
          fill
          loading="eager"
          fetchPriority="high"
          sizes="100vw"
          className="hero-image"
        />
        <div className="hero-shade" />
        <div className="hero-coordinate" aria-hidden="true">
          {profile.coordinates} <span>{profile.locationLabel}</span>
        </div>
        <div className="container hero-content">
          <div className="hero-eyebrow">
            <span />
            {l(
              "AT THE INTERSECTION OF SCIENCE & TECHNOLOGY",
              "GIAO THOA GIỮA KHOA HỌC & CÔNG NGHỆ",
            )}
          </div>
          <h1>
            {heading[0]}
            {heading.length > 1 && <><br /><span>{heading.slice(1).join('\n')}</span></>}
          </h1>
          <p>
            {page ? text(page.description) : l(
              "We explore the connections between artificial intelligence,\nEarth observation, and wireless technologies —\nto understand our world and build a better one.",
              "Chúng tôi khám phá kết nối giữa trí tuệ nhân tạo,\nquan sát Trái Đất và công nghệ không dây —\nđể hiểu thế giới và xây dựng một tương lai tốt hơn.",
            )}
          </p>
          <div className="hero-buttons">
            <Button asChild variant="light">
              <Link href={`/${locale}/research`}>
                {l("Explore our research", "Khám phá nghiên cứu")}
                <ArrowUpRight size={17} />
              </Link>
            </Button>
            <Link className="hero-secondary" href={`/${locale}/about`}>
              {l("Meet the laboratory", "Tìm hiểu phòng lab")}
              <ArrowRight size={17} />
            </Link>
          </div>
          <div className="hero-bottom">
            <span>
              <span className="live-dot" />
              {l(
                "CURIOSITY. COLLABORATION. DISCOVERY.",
                "TÒ MÒ. HỢP TÁC. KHÁM PHÁ.",
              )}
            </span>
            <a
              href="#research"
              aria-label={l("Scroll to research", "Đến phần nghiên cứu")}
            >
              <MoveDown size={20} />
            </a>
          </div>
        </div>
        <div className="hero-target" aria-hidden="true">
          <Plus />
          <span>EARTH OBSERVATION / 01</span>
        </div>
      </section>
      <DynamicSections />
      <div className="stats-strip">
        <div className="container stats-grid">
          {[
            [
              data.research.length,
              l("Research directions", "Hướng nghiên cứu"),
            ],
            [data.members.length, l("Curious minds", "Thành viên")],
            [data.publications.length, l("Sample publications", "Công bố mẫu")],
            [
              data.projects.length,
              l("Research concepts", "Ý tưởng nghiên cứu"),
            ],
          ].map(([number, label]) => (
            <div key={label}>
              <strong>
                {String(number).padStart(2, "0")}
                <span>↗</span>
              </strong>
              <span>{label}</span>
            </div>
          ))}
        </div>
      </div>
      <section className="section intro-section">
        <div className="container intro-grid">
          <div>
            <span className="eyebrow">
              <span />
              {l("WELCOME TO NEXUS", "CHÀO MỪNG ĐẾN NEXUS")}
            </span>
            <h2>
              {l("Different perspectives.", "Nhiều góc nhìn.")}
              <br />
              {l("Shared possibilities.", "Chung tiềm năng.")}
            </h2>
          </div>
          <div>
            <p className="intro-lead">
              {l(
                "A space for ambitious questions and interdisciplinary thinking.",
                "Không gian cho những câu hỏi lớn và tư duy liên ngành.",
              )}
            </p>
            <p>
              {l(
                "Nexus is an illustrative research laboratory bringing together researchers in AI, sensing, and connected systems. We believe meaningful progress happens when disciplines meet, ideas are shared, and research is grounded in real-world challenges.",
                "Nexus là phòng thí nghiệm minh họa kết nối các nhà nghiên cứu về AI, cảm biến và hệ thống kết nối. Chúng tôi tin rằng tiến bộ đến từ giao thoa lĩnh vực, chia sẻ ý tưởng và nghiên cứu gắn với thách thức thực tế.",
              )}
            </p>
            <Link href={`/${locale}/about`} className="text-link">
              {l("Discover our story", "Khám phá câu chuyện")}
              <ArrowUpRight size={17} />
            </Link>
          </div>
        </div>
      </section>
      <section className="section research-section" id="research">
        <div className="container">
          <SectionHeading
            eyebrow={l("WHAT WE EXPLORE", "LĨNH VỰC KHÁM PHÁ")}
            title={l(
              "Research without boundaries.",
              "Nghiên cứu không giới hạn.",
            )}
            description={l(
              "Connected disciplines. Open questions. A world of possibilities.",
              "Lĩnh vực kết nối. Câu hỏi rộng mở. Thế giới đầy tiềm năng.",
            )}
            href={`/${locale}/research`}
            linkLabel={l("All research areas", "Tất cả lĩnh vực")}
          />
          <div className="cards-grid three">
            {data.research.slice(0, 3).map((item, index) => (
              <ResearchCard key={item.id} item={item} index={index} />
            ))}
          </div>
        </div>
      </section>
      <section className="section publications-section">
        <div className="container">
          <SectionHeading
            eyebrow={l("IDEAS INTO KNOWLEDGE", "Ý TƯỞNG THÀNH TRI THỨC")}
            title={l("Selected publications.", "Công bố nổi bật.")}
            href={`/${locale}/publications`}
            linkLabel={l("Browse publications", "Xem các công bố")}
          />
          <div className="publication-list">
            {data.publications.slice(0, 3).map((item) => (
              <PublicationPreview key={item.id} item={item} />
            ))}
          </div>
          <p className="section-footnote">
            {l(
              "Illustrative publication records. No actual publication or academic achievement is claimed.",
              "Các bản ghi công bố minh họa. Không tuyên bố về công bố hay thành tích học thuật thực tế.",
            )}
          </p>
        </div>
      </section>
      <section className="section soft-section">
        <div className="container">
          <SectionHeading
            eyebrow={l(
              "FROM QUESTIONS TO POSSIBILITIES",
              "TỪ CÂU HỎI ĐẾN TIỀM NĂNG",
            )}
            title={l("Research in action.", "Nghiên cứu trong thực tiễn.")}
            href={`/${locale}/projects`}
            linkLabel={l("Explore projects", "Khám phá dự án")}
          />
          <div className="cards-grid three">
            {data.projects.map((item) => (
              <ProjectCard key={item.id} item={item} />
            ))}
          </div>
        </div>
      </section>
      <section className="section">
        <div className="container">
          <SectionHeading
            eyebrow={l(
              "THE PEOPLE BEHIND THE QUESTIONS",
              "CON NGƯỜI ĐẰNG SAU CÂU HỎI",
            )}
            title={l(
              "Great ideas start with people.",
              "Ý tưởng lớn bắt đầu từ con người.",
            )}
            href={`/${locale}/people`}
            linkLabel={l("Meet the team", "Gặp gỡ thành viên")}
          />
          <div className="cards-grid four">
            {data.members.slice(0, 4).map((item) => (
              <MemberCard key={item.id} item={item} />
            ))}
          </div>
        </div>
      </section>
      <section className="section soft-section">
        <div className="container">
          <SectionHeading
            eyebrow={l("LIFE AT THE LAB", "CUỘC SỐNG TẠI LAB")}
            title={l("The latest from Nexus.", "Tin mới từ Nexus.")}
            href={`/${locale}/news`}
            linkLabel={l("All news & events", "Tất cả tin & sự kiện")}
          />
          <div className="cards-grid three">
            {data.news.slice(0, 3).map((item) => (
              <NewsCard key={item.id} item={item} />
            ))}
          </div>
        </div>
      </section>
      <section className="partners-section">
        <div className="container">
          <span className="eyebrow">
            {l("BETTER, TOGETHER", "CÙNG NHAU, TỐT HƠN")}
          </span>
          <h2>{l("Built for collaboration.", "Xây dựng để hợp tác.")}</h2>
          <div className="partner-logos">
            {[
              [Globe2, "Academic institutions"],
              [Network, "Research networks"],
              [Cpu, "Technology partners"],
              [Layers, "Open science communities"],
            ].map(([Icon, title], index) => {
              const Logo = Icon as typeof Globe2;
              return (
                <span key={index}>
                  <Logo size={28} strokeWidth={1.4} />
                  {l(
                    title as string,
                    [
                      "Cơ sở học thuật",
                      "Mạng lưới nghiên cứu",
                      "Đối tác công nghệ",
                      "Cộng đồng khoa học mở",
                    ][index],
                  )}
                </span>
              );
            })}
          </div>
          <p>
            {l(
              "Illustrative collaboration categories · No institutional affiliations implied",
              "Nhóm hợp tác minh họa · Không ngụ ý liên kết tổ chức",
            )}
          </p>
        </div>
      </section>
    </>
  );
}
