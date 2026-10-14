import Link from "../site-link";
import { ArrowUpRight, Mail } from "lucide-react";
import { useLab } from "../lab-provider";
import { PageHeading } from "../shared";
import { LAB_BRAND } from '../../../../../shared/constants';

export function AboutView() {
  const { l, locale } = useLab();
  return (
    <>
      <PageHeading
        eyebrow={l("About the laboratory", "Giới thiệu phòng lab")}
        title={LAB_BRAND.name}
        description={LAB_BRAND.slogan}
        usePageContent={false}
      />
      <section className="section">
        <div className="container">
          <div className="about-grid">
            <div>
              <span className="eyebrow">
                <span />
                {l("OUR STORY", "CÂU CHUYỆN")}
              </span>
              <h2>{LAB_BRAND.name}</h2>
              <p>{LAB_BRAND.introduction[locale]}</p>
              <Link href={`/${locale}/people`} className="text-link">
                {l("Meet the ALELA team", "Gặp gỡ đội ngũ ALELA")}
                <ArrowUpRight size={17} />
              </Link>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}

export function ContactView() {
  const { l, profile } = useLab();
  return (
    <>
      <PageHeading
        eyebrow={l("Contact", "Liên hệ")}
        title={l(
          "Good research starts with a conversation.",
          "Nghiên cứu tốt bắt đầu từ trao đổi.",
        )}
        description=""
      />
      <section className="section">
        <div className="container">
          <h2>{l("Let’s connect.", "Cùng kết nối.")}</h2>
          {profile.email ? (
            <div className="contact-items">
              <div className="contact-item">
                <Mail size={22} />
                <div>
                  <h3>{l("General inquiries", "Liên hệ chung")}</h3>
                  <a href={`mailto:${profile.email}`}>{profile.email}</a>
                </div>
              </div>
            </div>
          ) : (
            <p>{l("Contact information is being updated.", "Thông tin liên hệ đang được cập nhật.")}</p>
          )}
        </div>
      </section>
    </>
  );
}
