export type Locale = 'en' | 'vi';
export interface LocalizedText { en: string; vi: string }
export interface Entity {
  id: string;
  title: LocalizedText;
  description: LocalizedText;
  visible?: boolean;
}
export interface ResearchArea extends Entity {
  labId?: number | null;
  image: string;
  icon: 'earth' | 'cpu' | 'radio' | 'network';
  topics: LocalizedText[];
}
export type MemberRole = 'Faculty' | 'Researcher' | 'PhD student' | 'MSc student' | 'Undergraduate' | 'Alumni';
export interface Member extends Entity {
  userId?: number | null;
  role: MemberRole | '';
  initials: string;
  area: string;
  email: string;
  color: string;
  image?: string;
}
export type PublicationType = 'Journal' | 'Conference' | 'Book' | 'Preprint';
export type ResourceKind = 'DOI' | 'PDF' | 'Code';
export interface ArticleSource {
  sourceName?: string | null;
  sourceUrl?: string | null;
}
export interface Publication extends Entity, ArticleSource {
  year: number | null;
  type: PublicationType | '';
  authors: string[];
  area: string;
  venue: LocalizedText;
  project: string;
  resources: ResourceKind[];
  links?: Partial<Record<ResourceKind, string>>;
  bodyHtml?: LocalizedText | null;
}
export interface Project extends Entity, ArticleSource {
  area: string;
  category: LocalizedText;
  status: 'Ongoing' | 'Completed' | '';
  period: string;
  image: string;
  lead: string;
  objectives: LocalizedText[];
  bodyHtml?: LocalizedText | null;
}
export interface NewsItem extends Entity, ArticleSource {
  category: 'News' | 'Seminar' | 'Achievement' | 'Event' | '';
  date: string;
  image: string;
  location: LocalizedText;
  body: LocalizedText[];
  bodyHtml?: LocalizedText | null;
}
export interface LabData {
  research: ResearchArea[];
  members: Member[];
  publications: Publication[];
  projects: Project[];
  news: NewsItem[];
}
export type CollectionName = keyof LabData;
export interface SiteProfile {
  name: string;
  shortName: string;
  email: string;
  coordinates: string;
  locationLabel: string;
  showDemoNotice: boolean;
  summary: LocalizedText;
}
export interface WebsiteContent {
  profile: SiteProfile;
  data: LabData;
  copy: Record<string, LocalizedText>;
  media: Record<string, string>;
  pages?: WebsitePage[];
  contentLibrary?: WebsiteSection[] | null;
}
export type PageTemplate = 'home' | 'about' | 'research' | 'people' | 'publications' | 'projects' | 'news' | 'join' | 'contact' | 'custom';
export interface WebsiteSection extends Entity {
  eyebrow: LocalizedText;
  body: LocalizedText[];
  bodyHtml?: LocalizedText | null;
  image: string;
  presentation: 'standard' | 'featured' | 'split';
  linkLabel: LocalizedText;
  linkUrl: string;
}
export interface WebsitePage extends Entity {
  slug: string;
  navigationTitle: LocalizedText;
  template: PageTemplate;
  showInNavigation: boolean;
  sections: WebsiteSection[];
  sectionIds?: string[] | null;
}
export interface WebsiteState {
  revision: number;
  draft: WebsiteContent | null;
  published: WebsiteContent | null;
  publishedAt: string | null;
}
export interface WebsiteSaveRequest { revision: number; content: WebsiteContent }
export interface PublishedWebsiteContent { content: WebsiteContent | null; publishedAt: string | null }
