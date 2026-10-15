import Image from "../site-image";
import Link from "../site-link";
import {
  ArrowUpRight,
  ArrowRight,
  MoveDown,
} from "lucide-react";
import { useLab } from "../lab-provider";
import { Button } from "../ui/button";
import { DynamicSections } from '../dynamic-sections';
import { LAB_BRAND } from '../../../../../shared/constants';
import {
  SectionHeading,
  ResearchCard,
  PublicationPreview,
  ProjectCard,
  NewsCard,
  MemberCard,
} from "../shared";
export function HomeView() {
  const { locale, l, data, text, page, homeHero: hero } = useLab();
  const legacyHero = page?.hero == null;
  return (
    <>
      <section className="hero hero-alela">
        {hero.backgroundImage && <Image
          src={hero.backgroundImage}
          alt={text(hero.backgroundAlt)}
          fill
          loading="eager"
          fetchPriority="high"
          sizes="100vw"
          className="hero-image"
        />}
        <div className="hero-shade" />
        <div className="container hero-content">
          <div className="hero-eyebrow">
            <span />
            {text(hero.eyebrow)}
          </div>
          <h1>
            {text(hero.title)}
          </h1>
          <p>
            {text(hero.description)}
          </p>
          <div className="hero-buttons">
            {(legacyHero || text(hero.primaryCta.label).trim()) && text(hero.primaryCta.url) && <Button asChild variant="light">
              <Link href={text(hero.primaryCta.url)} resolveTemplate={false}>
                {text(hero.primaryCta.label)}
                <ArrowUpRight size={17} />
              </Link>
            </Button>}
            {(legacyHero || text(hero.secondaryCta.label).trim()) && text(hero.secondaryCta.url) && <Link className="hero-secondary" href={text(hero.secondaryCta.url)} resolveTemplate={false}>
              {text(hero.secondaryCta.label)}
              <ArrowRight size={17} />
            </Link>}
          </div>
          <div className="hero-bottom">
            <span>
              <span className="live-dot" />
              {text(hero.footerText)}
            </span>
            {data.research.length > 0 && <a
              href="#research"
              aria-label={l("Scroll to research", "Đến phần nghiên cứu")}
            >
              <MoveDown size={20} />
            </a>}
          </div>
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
            [data.publications.length, l("Publications", "Công bố")],
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
              {l("WELCOME TO ALELA", "CHÀO MỪNG ĐẾN ALELA")}
            </span>
            <h2>
              {LAB_BRAND.slogan}
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
              {LAB_BRAND.summary[locale]}
            </p>
            <Link href={`/${locale}/about`} className="text-link">
              {l("Discover our story", "Khám phá câu chuyện")}
              <ArrowUpRight size={17} />
            </Link>
          </div>
        </div>
      </section>
      {data.research.length > 0 && <section className="section research-section" id="research">
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
      </section>}
      {data.publications.length > 0 && <section className="section publications-section">
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
        </div>
      </section>}
      {data.projects.length > 0 && <section className="section soft-section">
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
      </section>}
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
          <div
            className="member-carousel"
            role="region"
            aria-label={l("All members — scroll horizontally", "Tất cả thành viên — cuộn ngang để xem")}
            tabIndex={0}
          >
            {data.members.map((item) => (
              <MemberCard key={item.id} item={item} />
            ))}
          </div>
        </div>
      </section>
      {data.news.length > 0 && <section className="section soft-section">
        <div className="container">
          <SectionHeading
            eyebrow={l("LIFE AT THE LAB", "CUỘC SỐNG TẠI LAB")}
            title={l("The latest from ALELA.", "Tin mới từ ALELA.")}
            href={`/${locale}/news`}
            linkLabel={l("All news & events", "Tất cả tin & sự kiện")}
          />
          <div className="cards-grid three">
            {data.news.slice(0, 3).map((item) => (
              <NewsCard key={item.id} item={item} />
            ))}
          </div>
        </div>
      </section>}
    </>
  );
}
