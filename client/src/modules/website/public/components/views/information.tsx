import Image from "../site-image";
import Link from "../site-link";
import { useState } from "react";
import {
  ArrowUpRight,
  ArrowRight,
  Compass,
  Eye,
  FlaskConical,
  Cpu,
  Radio,
  BookOpen,
  Mail,
  MapPin,
  Clock,
  CheckCircle2,
  Send,
  GraduationCap,
} from "lucide-react";
import { useLab } from "../lab-provider";
import { PageHeading, SectionHeading, DemoNotice } from "../shared";
import { Button } from "../ui/button";
import { Select } from "../ui/select";
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
            <div className="about-image">
              <Image
                src="/images/team.jpg"
                alt={l(
                  "Illustrative photograph of people collaborating around a table",
                  "Ảnh minh họa nhóm làm việc quanh bàn",
                )}
                fill
                sizes="(max-width: 800px) 100vw, 50vw"
              />
              <span className="photo-caption">
                {l("Illustrative stock photograph", "Ảnh tư liệu minh họa")}
              </span>
            </div>
            <div>
              <span className="eyebrow">
                <span />
                {l("OUR STORY", "CÂU CHUYỆN")}
              </span>
              <h2>
                {LAB_BRAND.name}
              </h2>
              <p>
                {LAB_BRAND.introduction[locale]}
              </p>
              <Link href={`/${locale}/people`} className="text-link">
                {l("Meet the ALELA team", "Gặp gỡ đội ngũ ALELA")}
                <ArrowUpRight size={17} />
              </Link>
            </div>
          </div>
          <div className="value-grid">
            {[
              [
                Compass,
                l("Our mission", "Sứ mệnh"),
                l(
                  "Advance useful, responsible research by connecting foundational science with real-world questions.",
                  "Thúc đẩy nghiên cứu hữu ích, có trách nhiệm qua kết nối khoa học nền tảng với câu hỏi thực tiễn.",
                ),
              ],
              [
                Eye,
                l("Our vision", "Tầm nhìn"),
                l(
                  "A connected research community where knowledge is open, collaboration is natural, and technology serves people.",
                  "Cộng đồng nghiên cứu kết nối, tri thức mở, hợp tác tự nhiên và công nghệ phục vụ con người.",
                ),
              ],
              [
                BookOpen,
                l("Our approach", "Phương pháp"),
                l(
                  "Ask carefully. Build transparently. Evaluate honestly. Share what we learn, including the questions that remain.",
                  "Đặt câu hỏi kỹ lưỡng. Xây dựng minh bạch. Đánh giá trung thực. Chia sẻ điều đã học và câu hỏi còn lại.",
                ),
              ],
            ].map(([Icon, title, description], index) => {
              const Symbol = Icon as typeof Compass;
              return (
                <div key={index}>
                  <Symbol size={25} />
                  <h3>{title as string}</h3>
                  <p>{description as string}</p>
                </div>
              );
            })}
          </div>
        </div>
      </section>
      <section className="section soft-section">
        <div className="container">
          <SectionHeading
            eyebrow={l("SPACE TO EXPLORE", "KHÔNG GIAN KHÁM PHÁ")}
            title={l(
              "Tools for better questions.",
              "Công cụ cho câu hỏi tốt hơn.",
            )}
            description={l(
              "Illustrative facilities for the laboratory concept. Equipment and resources are examples, not a verified inventory.",
              "Cơ sở vật chất minh họa. Thiết bị và tài nguyên là ví dụ, không phải danh mục đã xác minh.",
            )}
          />
          <div className="facilities-grid">
            <div className="facility-photo">
              <Image
                src="/images/campus.jpg"
                alt={l(
                  "Illustrative shared workspace",
                  "Không gian làm việc chung minh họa",
                )}
                fill
                sizes="(max-width: 800px) 100vw, 50vw"
              />
            </div>
            <div>
              {[
                [
                  Cpu,
                  l("Computing & AI workspace", "Không gian tính toán & AI"),
                  l(
                    "Example GPU workstations and reproducible experiment environments.",
                    "Máy trạm GPU mẫu và môi trường thí nghiệm có thể tái lập.",
                  ),
                ],
                [
                  Radio,
                  l(
                    "Sensing & wireless testbed",
                    "Hệ thống thử nghiệm cảm biến & không dây",
                  ),
                  l(
                    "Example sensor kits and networking equipment for hands-on prototyping.",
                    "Bộ cảm biến và thiết bị mạng mẫu để xây dựng nguyên mẫu.",
                  ),
                ],
                [
                  FlaskConical,
                  l(
                    "Collaborative research space",
                    "Không gian nghiên cứu cộng tác",
                  ),
                  l(
                    "Shared desks, discussion areas, and a seminar room concept.",
                    "Bàn làm việc chung, khu thảo luận và ý tưởng phòng hội thảo.",
                  ),
                ],
              ].map(([Icon, title, desc], i) => {
                const Symbol = Icon as typeof Cpu;
                return (
                  <div className="facility-item" key={i}>
                    <Symbol size={22} />
                    <div>
                      <h3>{title as string}</h3>
                      <p>{desc as string}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
const opportunities = [
  "PhD research",
  "MSc research",
  "Undergraduate research",
  "Research collaboration",
];
const opportunitiesVi = [
  "Nghiên cứu tiến sĩ",
  "Nghiên cứu thạc sĩ",
  "Nghiên cứu sinh viên",
  "Hợp tác nghiên cứu",
];
export function JoinView() {
  const { l } = useLab();
  const [opportunity, setOpportunity] = useState(opportunities[0]);
  return (
    <>
      <PageHeading
        eyebrow={l("Join us", "Tham gia")}
        title={l(
          "Your questions belong here.",
          "Câu hỏi của bạn thuộc về nơi đây.",
        )}
        description={l(
          "Explore example pathways into research. Bring an open mind, a thoughtful question, and a willingness to learn.",
          "Khám phá con đường nghiên cứu minh họa. Mang theo tư duy rộng mở, câu hỏi sâu sắc và tinh thần học hỏi.",
        )}
      />
      <section className="section">
        <div className="container">
          <DemoNotice />
          <div className="opportunities-grid">
            {opportunities.map((item, i) => (
              <article className="opportunity-card" key={item}>
                <GraduationCap size={26} />
                <span className="label">
                  0{i + 1} / {l("RESEARCH PATHWAY", "CON ĐƯỜNG NGHIÊN CỨU")}
                </span>
                <h3>{l(item, opportunitiesVi[i])}</h3>
                <p>
                  {l(
                    [
                      "Develop an independent research direction with sustained mentorship and interdisciplinary exchange.",
                      "Explore a focused research question through experiments, reading, and a thesis project.",
                      "Get hands-on experience with research methods, prototyping, and a supportive team.",
                      "Connect complementary expertise through an open, carefully scoped research conversation.",
                    ][i],
                    [
                      "Phát triển hướng nghiên cứu độc lập với sự hướng dẫn và trao đổi liên ngành.",
                      "Khám phá câu hỏi nghiên cứu qua thí nghiệm, đọc tài liệu và luận văn.",
                      "Trải nghiệm phương pháp nghiên cứu, nguyên mẫu và nhóm hỗ trợ.",
                      "Kết nối chuyên môn bổ trợ qua trao đổi nghiên cứu mở, xác định phạm vi rõ ràng.",
                    ][i],
                  )}
                </p>
                <a
                  href="#inquiry"
                  className="text-link"
                  onClick={() => setOpportunity(item)}
                >
                  {l("Explore this pathway", "Khám phá con đường này")}
                  <ArrowUpRight size={16} />
                </a>
              </article>
            ))}
          </div>
          <div className="join-form-grid" id="inquiry">
            <div>
              <span className="eyebrow">
                <span />
                {l("START A CONVERSATION", "BẮT ĐẦU TRAO ĐỔI")}
              </span>
              <h2>
                {l(
                  "Tell us what makes\nyou curious.",
                  "Chia sẻ điều khiến\nbạn tò mò.",
                )}
              </h2>
              <p>
                {l(
                  "For a real application, a laboratory would typically ask for a short research statement, a CV, and relevant academic experience. This demo form previews an inquiry without sending it.",
                  "Hồ sơ thực tế thường cần đề xuất nghiên cứu ngắn, CV và kinh nghiệm học thuật liên quan. Biểu mẫu mẫu này xem trước liên hệ mà không gửi đi.",
                )}
              </p>
              <ol className="steps">
                <li>
                  <span>01</span>
                  {l(
                    "Explore a research direction",
                    "Tìm hiểu hướng nghiên cứu",
                  )}
                </li>
                <li>
                  <span>02</span>
                  {l(
                    "Share your background and interests",
                    "Chia sẻ nền tảng và mối quan tâm",
                  )}
                </li>
                <li>
                  <span>03</span>
                  {l(
                    "Start an informed conversation",
                    "Bắt đầu trao đổi có cơ sở",
                  )}
                </li>
              </ol>
            </div>
            <InquiryForm
              application
              opportunity={opportunity}
              setOpportunity={setOpportunity}
            />
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
        description={l(
          "Questions, ideas, or a potential collaboration? Try our demonstration contact experience.",
          "Có câu hỏi, ý tưởng hay cơ hội hợp tác? Hãy thử trải nghiệm liên hệ minh họa.",
        )}
      />
      <section className="section">
        <div className="container contact-grid">
          <div>
            <h2>{l("Let’s connect.", "Cùng kết nối.")}</h2>
            <p>
              {l(
                "The following address and email are placeholders for a future laboratory deployment.",
                "Địa chỉ và email dưới đây là thông tin mẫu cho phòng lab trong tương lai.",
              )}
            </p>
            <div className="contact-items">
              {[
                [
                  MapPin,
                  l("Visit the lab", "Ghé thăm phòng lab"),
                  l(
                    "Demo Research Building, Hanoi, Vietnam",
                    "Tòa nhà Nghiên cứu minh họa, Hà Nội, Việt Nam",
                  ),
                ],
                [
                  Mail,
                  l("General inquiries", "Liên hệ chung"),
                  profile.email,
                ],
                [
                  Clock,
                  l("Example office hours", "Giờ làm việc mẫu"),
                  l(
                    "Monday–Friday · 09:00–17:00 (ICT)",
                    "Thứ Hai–Thứ Sáu · 09:00–17:00 (ICT)",
                  ),
                ],
              ].map(([Icon, title, desc], i) => {
                const Symbol = Icon as typeof Mail;
                return (
                  <div className="contact-item" key={i}>
                    <Symbol size={22} />
                    <div>
                      <h3>{title as string}</h3>
                      <p>{desc as string}</p>
                    </div>
                  </div>
                );
              })}
            </div>
            <div
              className="map-preview"
              role="img"
              aria-label={l(
                "Illustrative location diagram, not a real navigational map",
                "Sơ đồ vị trí minh họa, không dùng để điều hướng",
              )}
            >
              <div className="map-river" />
              <div className="map-road road-one" />
              <div className="map-road road-two" />
              <div className="map-road road-three" />
              <span className="map-marker">
                <MapPin size={28} fill="#173b50" />
                <strong>{LAB_BRAND.shortName}</strong>
              </span>
              <span className="map-caption">
                {l("ILLUSTRATIVE LOCATION · HANOI", "VỊ TRÍ MINH HỌA · HÀ NỘI")}
              </span>
            </div>
          </div>
          <InquiryForm />
        </div>
      </section>
    </>
  );
}
export function InquiryForm({
  application = false,
  opportunity = opportunities[0],
  setOpportunity,
}: {
  application?: boolean;
  opportunity?: string;
  setOpportunity?: (v: string) => void;
}) {
  const { l } = useLab();
  const [preview, setPreview] = useState<{
    name: string;
    email: string;
    message: string;
    subject: string;
  } | null>(null);
  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPreview({
      name: String(form.get("name")),
      email: String(form.get("email")),
      message: String(form.get("message")),
      subject: application ? opportunity : String(form.get("subject")),
    });
  }
  if (preview)
    return (
      <div className="inquiry-form form-success" role="status">
        <CheckCircle2 size={40} />
        <h3>
          {l(
            "Your inquiry preview is ready.",
            "Bản xem trước liên hệ đã sẵn sàng.",
          )}
        </h3>
        <p>
          {l(
            "No message was sent. This frontend demo does not submit or store your information.",
            "Chưa gửi tin nhắn. Frontend minh họa không gửi hay lưu thông tin của bạn.",
          )}
        </p>
        <dl>
          <dt>{l("Name", "Họ tên")}</dt>
          <dd>{preview.name}</dd>
          <dt>Email</dt>
          <dd>{preview.email}</dd>
          <dt>{l("Subject", "Chủ đề")}</dt>
          <dd>
            {application
              ? l(
                  preview.subject,
                  opportunitiesVi[opportunities.indexOf(preview.subject)],
                )
              : preview.subject}
          </dd>
          <dt>{l("Message", "Nội dung")}</dt>
          <dd className="preview-message">{preview.message}</dd>
        </dl>
        <Button onClick={() => setPreview(null)} variant="outline">
          {l("Write another inquiry", "Viết liên hệ khác")}
          <ArrowRight size={16} />
        </Button>
      </div>
    );
  return (
    <form className="inquiry-form" onSubmit={submit}>
      <h3>
        {application
          ? l("Research interest form", "Biểu mẫu quan tâm nghiên cứu")
          : l("Send us a note", "Gửi lời nhắn")}
      </h3>
      <p>
        {l("Fields marked * are required.", "Các trường có dấu * là bắt buộc.")}
      </p>
      <label>
        {l("Full name", "Họ và tên")} *
        <input
          name="name"
          autoComplete="name"
          required
          minLength={2}
          maxLength={100}
          placeholder={l("Your name", "Họ tên của bạn")}
        />
      </label>
      <label>
        {l("Email address", "Địa chỉ email")} *
        <input
          name="email"
          autoComplete="email"
          type="email"
          required
          maxLength={150}
          placeholder="you@example.com"
        />
      </label>
      {application ? (
        <div className="form-label">
          {l("I’m interested in", "Tôi quan tâm đến")} *
          <Select
            value={opportunity}
            onValueChange={setOpportunity || (() => {})}
            label={l("Research pathway", "Con đường nghiên cứu")}
            options={opportunities.map((v, i) => ({
              value: v,
              label: l(v, opportunitiesVi[i]),
            }))}
          />
        </div>
      ) : (
        <label>
          {l("Subject", "Chủ đề")} *
          <input
            name="subject"
            required
            maxLength={150}
            placeholder={l(
              "What would you like to discuss?",
              "Bạn muốn trao đổi điều gì?",
            )}
          />
        </label>
      )}
      <label>
        {application
          ? l(
              "Your background & research interests",
              "Nền tảng & mối quan tâm nghiên cứu",
            )
          : l("Message", "Nội dung")}{" "}
        *
        <textarea
          name="message"
          required
          minLength={10}
          maxLength={3000}
          rows={5}
          placeholder={l(
            "Tell us a little more…",
            "Chia sẻ thêm với chúng tôi…",
          )}
        />
      </label>
      <label className="checkbox-label">
        <input type="checkbox" required />
        {l(
          "I understand this is a demo and no inquiry will be sent.",
          "Tôi hiểu đây là bản minh họa và không gửi liên hệ thực tế.",
        )}
      </label>
      <Button type="submit">
        {l("Preview inquiry", "Xem trước liên hệ")}
        <Send size={16} />
      </Button>
      <span className="form-footnote">
        {l(
          "Demonstration only · No data is persisted or transmitted",
          "Chỉ minh họa · Không lưu hay truyền dữ liệu",
        )}
      </span>
    </form>
  );
}
