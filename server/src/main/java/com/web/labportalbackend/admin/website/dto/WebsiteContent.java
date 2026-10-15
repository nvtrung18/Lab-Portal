package com.web.labportalbackend.admin.website.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.web.labportalbackend.admin.website.validation.SafeWebsiteHtml;

import java.util.List;
import java.util.Map;

public record WebsiteContent(
        @Schema(description = "Website identity and demonstration labels") @NotNull @Valid SiteProfile profile,
        @Schema(description = "Public editorial collections") @NotNull @Valid LabData data,
        @Schema(description = "Bilingual page text indexed by the template text key")
        @NotNull @Size(max = 500) Map<@Size(max = 2000) String, @NotNull @Valid LocalizedText> copy,
        @Schema(description = "Image replacements indexed by the original template asset path")
        @NotNull @Size(max = 50) Map<@Size(max = 200) String, @NotNull @Pattern(regexp = ASSET_URL) String> media,
        @Schema(description = "Pages in display order; null retains the legacy template pages")
        @Size(max = 50) List<@NotNull @Valid WebsitePage> pages,
        @Schema(description = "Reusable editorial content; null retains legacy page sections")
        @Size(max = 5000) List<@NotNull @Valid WebsiteSection> contentLibrary
) {
    public static final String ASSET_URL = "^$|^/(?!/)[^\\s]*$|^https?://[^\\s]+$";
    public static final String HTTP_URL = "^$|^https?://[^\\s]+$";
    public static final String PAGE_LINK_URL = "^$|^/(?!/)[^\\s]*$|^#[^\\s]*$|^https?://[^\\s]+$";

    public record WebsitePage(
            @Schema(description = "Stable page identifier") @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}") String id,
            @Schema(description = "URL segment; empty selects the homepage") @NotNull @Pattern(regexp = "^$|[a-z0-9][a-z0-9-]{0,79}") String slug,
            @Schema(description = "Editable page heading") @NotNull @Valid LocalizedText title,
            @Schema(description = "Page introduction") @NotNull @Valid LocalizedText description,
            @Schema(description = "Navigation label; empty uses the page heading") @NotNull @Valid LocalizedText navigationTitle,
            @Schema(description = "Template body or custom editorial content") @NotNull @Pattern(regexp = "home|about|research|people|publications|projects|products|news|join|contact|custom") String template,
            @Schema(description = "Whether the page is public; null means visible") Boolean visible,
            @Schema(description = "Whether the page appears in the main menu") boolean showInNavigation,
            @Schema(description = "Legacy or resolved editorial sections in display order") @NotNull @Size(max = 100) List<@NotNull @Valid WebsiteSection> sections,
            @Schema(description = "Selected library identifiers in display order; null uses legacy sections")
            @Size(max = 100) List<@NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}") String> sectionIds
    ) {}

    public record WebsiteSection(
            @Schema(description = "Stable section identifier") @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}") String id,
            @Schema(description = "Section heading") @NotNull @Valid LocalizedText title,
            @Schema(description = "Section summary") @NotNull @Valid LocalizedText description,
            @Schema(description = "Caption above the heading") @NotNull @Valid LocalizedText eyebrow,
            @Schema(description = "Editorial paragraphs in display order") @NotNull @Size(max = 200) List<@NotNull @Valid LocalizedText> body,
            @Schema(description = "Optional section image URL") @NotNull @Size(max = 2000) @Pattern(regexp = ASSET_URL) String image,
            @Schema(description = "Section visual treatment", allowableValues = {"standard", "featured", "split"}) @NotNull @Pattern(regexp = "standard|featured|split") String presentation,
            @Schema(description = "Optional action label") @NotNull @Valid LocalizedText linkLabel,
            @Schema(description = "Optional HTTP URL, internal path or anchor") @NotNull @Size(max = 2000) @Pattern(regexp = PAGE_LINK_URL) String linkUrl,
            @Schema(description = "Whether the section is public; null means visible") Boolean visible,
            @Schema(description = "Formatted editorial body; null uses legacy paragraphs, empty text clears the body") @Valid RichTextContent bodyHtml,
            @Schema(description = "Additional article images in display order; null retains legacy cover-only content")
            @Size(max = 15) List<@NotNull @Valid GalleryImage> gallery,
            @Schema(description = "Optional bilingual cover alt text") @Valid ImageText imageAlt,
            @Schema(description = "Optional bilingual cover caption") @Valid ImageText imageCaption
    ) {}

    public record ImageText(
            @NotNull @Size(max = 2000) String en,
            @NotNull @Size(max = 2000) String vi
    ) {}

    public record GalleryImage(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}") String id,
            @NotBlank @Size(max = 2000) @Pattern(regexp = ASSET_URL) String src,
            @NotNull @Valid ImageText alt,
            @NotNull @Valid ImageText caption
    ) {}

    public record LocalizedText(
            @Schema(description = "English text; empty text is allowed") @NotNull @Size(max = 20000) String en,
            @Schema(description = "Vietnamese text; empty text is allowed") @NotNull @Size(max = 20000) String vi
    ) {}

    public record RichTextContent(
            @Schema(description = "English formatted HTML body; empty text is allowed") @NotNull @Size(max = 200000) @SafeWebsiteHtml String en,
            @Schema(description = "Vietnamese formatted HTML body; empty text is allowed") @NotNull @Size(max = 200000) @SafeWebsiteHtml String vi
    ) {}

    public record SiteProfile(
            @Schema(description = "Full laboratory name") @NotNull @Size(max = 200) String name,
            @Schema(description = "Name displayed beside the website logo") @NotNull @Size(max = 60) String shortName,
            @Schema(description = "Public contact email; may be empty") @NotNull @Email @Size(max = 254) String email,
            @Schema(description = "Coordinate caption in the homepage banner") @NotNull @Size(max = 200) String coordinates,
            @Schema(description = "Location caption in the homepage banner") @NotNull @Size(max = 200) String locationLabel,
            @Schema(description = "Whether to display the template demonstration notices") boolean showDemoNotice,
            @Schema(description = "Bilingual website description used in page metadata") @NotNull @Valid LocalizedText summary
    ) {}

    public record LabData(
            @Schema(description = "Research directions in display order") @NotNull @Size(max = 200) List<@NotNull @Valid ResearchArea> research,
            @Schema(description = "Public member profiles in display order") @NotNull @Size(max = 500) List<@NotNull @Valid Member> members,
            @Schema(description = "Publication records in display order") @NotNull @Size(max = 1000) List<@NotNull @Valid Publication> publications,
            @Schema(description = "Public project summaries in display order") @NotNull @Size(max = 500) List<@NotNull @Valid Project> projects,
            @Schema(description = "News and event records in display order") @NotNull @Size(max = 1000) List<@NotNull @Valid NewsItem> news
    ) {}

    public record ResearchArea(
            @Schema(description = "Stable detail page identifier") @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}") String id,
            @Schema(description = "Direction name") @NotNull @Valid LocalizedText title,
            @Schema(description = "Direction summary") @NotNull @Valid LocalizedText description,
            @Schema(description = "Whether the record is visible on the published website; null means visible") Boolean visible,
            @Schema(description = "Direction image URL or asset path") @NotNull @Pattern(regexp = ASSET_URL) String image,
            @Schema(description = "Template icon", allowableValues = {"earth", "cpu", "radio", "network"}) @NotNull @Pattern(regexp = "earth|cpu|radio|network") String icon,
            @Schema(description = "Research subjects in display order") @NotNull @Size(max = 100) List<@NotNull @Valid LocalizedText> topics,
            @Schema(description = "Linked research group identifier using the existing laboratory ID; null for legacy content and public responses") @Positive Long labId,
            @Schema(description = "Additional article images in display order; null retains legacy cover-only content")
            @Size(max = 15) List<@NotNull @Valid GalleryImage> gallery,
            @Schema(description = "Optional bilingual cover alt text") @Valid ImageText imageAlt,
            @Schema(description = "Optional bilingual cover caption") @Valid ImageText imageCaption
    ) {}

    public record Member(
            @Schema(description = "Stable profile page identifier") @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}") String id,
            @Schema(description = "Public display name") @NotNull @Valid LocalizedText title,
            @Schema(description = "Public biography") @NotNull @Valid LocalizedText description,
            @Schema(description = "Whether the public profile is visible; null means visible") Boolean visible,
            @Schema(description = "Laboratory membership role; empty and legacy academic roles are accepted", allowableValues = {"Steering Member", "Core Member", "PhD student", "MSc student", "Undergraduate", "Guest", "Faculty", "Researcher", "Alumni", ""})
            @NotNull @Pattern(regexp = "^$|Steering Member|Core Member|PhD student|MSc student|Undergraduate|Guest|Faculty|Researcher|Alumni") String role,
            @Schema(description = "Letters displayed when there is no portrait") @NotNull @Size(max = 10) String initials,
            @Schema(description = "Related research direction identifier; may be empty") @NotNull @Size(max = 80) String area,
            @Schema(description = "Public email; may be empty") @NotNull @Email @Size(max = 254) String email,
            @Schema(description = "Avatar background color; may be empty") @NotNull @Pattern(regexp = "^$|#[0-9a-fA-F]{6}") String color,
            @Schema(description = "Optional portrait URL or asset path") @Pattern(regexp = ASSET_URL) String image,
            @Schema(description = "Linked account identifier; null for legacy profiles and public responses") @Positive Long userId,
            @Schema(description = "Public academic profile snapshot; null when not supplied") @Valid ScholarProfile scholarProfile
    ) {}

    public record ScholarProfile(
            @Schema(description = "Google Scholar profile URL") @NotBlank @Size(max = 2000) @Pattern(regexp = HTTP_URL) String url,
            @Schema(description = "Public position and institution") @NotNull @Valid LocalizedText affiliation,
            @Schema(description = "Research interests in display order") @NotNull @Size(max = 100) List<@NotNull @Valid LocalizedText> interests,
            @Schema(description = "Public personal website URL; null or empty when not supplied") @Size(max = 2000) @Pattern(regexp = HTTP_URL) String homepageUrl,
            @Schema(description = "Snapshot date in yyyy-MM-dd format") @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String retrievedOn,
            @Schema(description = "Citation metrics; null when not supplied") @Valid ScholarMetrics metrics,
            @Schema(description = "Selected Scholar articles in display order") @NotNull @Size(max = 100) List<@NotNull @Valid ScholarPublication> publications
    ) {}

    public record ScholarMetrics(
            @Schema(description = "All-time citations; null when unavailable") @Min(0) Integer citations,
            @Schema(description = "All-time h-index; null when unavailable") @Min(0) Integer hIndex,
            @Schema(description = "All-time i10-index; null when unavailable") @Min(0) Integer i10Index,
            @Schema(description = "Starting year of the recent metrics; null when unavailable") @Min(1) @Max(9999) Integer sinceYear,
            @Schema(description = "Citations since the starting year; null when unavailable") @Min(0) Integer recentCitations,
            @Schema(description = "h-index since the starting year; null when unavailable") @Min(0) Integer recentHIndex,
            @Schema(description = "i10-index since the starting year; null when unavailable") @Min(0) Integer recentI10Index
    ) {}

    public record ScholarPublication(
            @Schema(description = "Article title as listed on Scholar") @NotBlank @Size(max = 2000) String title,
            @Schema(description = "Author text as listed on Scholar; may be abbreviated") @NotNull @Size(max = 2000) String authors,
            @Schema(description = "Publication venue as listed on Scholar; may be abbreviated") @NotNull @Size(max = 2000) String venue,
            @Schema(description = "Article year; null when unavailable") @Min(1) @Max(9999) Integer year,
            @Schema(description = "Article citations; null when not displayed on Scholar") @Min(0) Integer citations,
            @Schema(description = "Original Scholar article URL") @NotBlank @Size(max = 2000) @Pattern(regexp = HTTP_URL) String url
    ) {}

    public enum ResourceKind { DOI, PDF, Code }

    public record Publication(
            @Schema(description = "Stable publication page identifier") @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}") String id,
            @Schema(description = "Publication title") @NotNull @Valid LocalizedText title,
            @Schema(description = "Publication abstract or summary") @NotNull @Valid LocalizedText description,
            @Schema(description = "Whether the publication is visible; null means visible") Boolean visible,
            @Schema(description = "Publication year; null when not supplied") @Min(1) @Max(9999) Integer year,
            @Schema(description = "Publication format; may be empty") @NotNull @Pattern(regexp = "^$|Journal|Conference|Book|Preprint") String type,
            @Schema(description = "Contributor profile identifiers or external contributor names") @NotNull @Size(max = 100) List<@NotNull @Size(max = 200) String> authors,
            @Schema(description = "Related research direction identifier; may be empty") @NotNull @Size(max = 80) String area,
            @Schema(description = "Journal, conference, publisher or venue") @NotNull @Valid LocalizedText venue,
            @Schema(description = "Related project identifier; may be empty") @NotNull @Size(max = 80) String project,
            @Schema(description = "Resource buttons shown by the template") @NotNull @Size(max = 3) List<@NotNull ResourceKind> resources,
            @Schema(description = "Optional real resource links; absent links retain the template preview")
            @Size(max = 3) Map<ResourceKind, @NotNull @Pattern(regexp = HTTP_URL) String> links,
            @Schema(description = "Formatted publication details; omitted, null or empty text means no additional body") @Valid RichTextContent bodyHtml,
            @Schema(description = "Source outlet name; omitted, null or empty means no source label") @Size(max = 200) String sourceName,
            @Schema(description = "Original publication HTTP(S) URL; omitted, null or empty means no source link") @Size(max = 2000) @Pattern(regexp = HTTP_URL) String sourceUrl
    ) {}

    public record Project(
            @Schema(description = "Stable public project page identifier") @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}") String id,
            @Schema(description = "Public project title") @NotNull @Valid LocalizedText title,
            @Schema(description = "Public project summary") @NotNull @Valid LocalizedText description,
            @Schema(description = "Whether the summary is visible; null means visible") Boolean visible,
            @Schema(description = "Related research direction identifier; may be empty") @NotNull @Size(max = 80) String area,
            @Schema(description = "Public project category") @NotNull @Valid LocalizedText category,
            @Schema(description = "Project status; may be empty") @NotNull @Pattern(regexp = "^$|Ongoing|Completed") String status,
            @Schema(description = "Displayed project period; may be empty") @NotNull @Size(max = 200) String period,
            @Schema(description = "Project image URL or asset path") @NotNull @Pattern(regexp = ASSET_URL) String image,
            @Schema(description = "Public lead profile identifier; may be empty") @NotNull @Size(max = 80) String lead,
            @Schema(description = "Project objectives in display order") @NotNull @Size(max = 100) List<@NotNull @Valid LocalizedText> objectives,
            @Schema(description = "Formatted project details; omitted, null or empty text means no additional body") @Valid RichTextContent bodyHtml,
            @Schema(description = "Source outlet name; omitted, null or empty means no source label") @Size(max = 200) String sourceName,
            @Schema(description = "Original project HTTP(S) URL; omitted, null or empty means no source link") @Size(max = 2000) @Pattern(regexp = HTTP_URL) String sourceUrl,
            @Schema(description = "Additional article images in display order; null retains legacy cover-only content")
            @Size(max = 15) List<@NotNull @Valid GalleryImage> gallery,
            @Schema(description = "Optional bilingual cover alt text") @Valid ImageText imageAlt,
            @Schema(description = "Optional bilingual cover caption") @Valid ImageText imageCaption
    ) {}

    public record NewsItem(
            @Schema(description = "Stable news detail page identifier") @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}") String id,
            @Schema(description = "News or event title") @NotNull @Valid LocalizedText title,
            @Schema(description = "News or event summary") @NotNull @Valid LocalizedText description,
            @Schema(description = "Whether the article is visible; null means visible") Boolean visible,
            @Schema(description = "Article category; may be empty") @NotNull @Pattern(regexp = "^$|News|Seminar|Achievement|Event") String category,
            @Schema(description = "Article or event date in yyyy-MM-dd; may be empty") @NotNull @Pattern(regexp = "^$|\\d{4}-\\d{2}-\\d{2}") String date,
            @Schema(description = "Article image URL or asset path") @NotNull @Pattern(regexp = ASSET_URL) String image,
            @Schema(description = "Event location") @NotNull @Valid LocalizedText location,
            @Schema(description = "Legacy article paragraphs in display order") @NotNull @Size(max = 200) List<@NotNull @Valid LocalizedText> body,
            @Schema(description = "Formatted article body; null uses legacy paragraphs, empty text clears the body") @Valid RichTextContent bodyHtml,
            @Schema(description = "Source outlet name; omitted, null or empty means no source label") @Size(max = 200) String sourceName,
            @Schema(description = "Original article HTTP(S) URL; omitted, null or empty means no source link") @Size(max = 2000) @Pattern(regexp = HTTP_URL) String sourceUrl,
            @Schema(description = "Additional article images in display order; null retains legacy cover-only content")
            @Size(max = 15) List<@NotNull @Valid GalleryImage> gallery,
            @Schema(description = "Optional bilingual cover alt text") @Valid ImageText imageAlt,
            @Schema(description = "Optional bilingual cover caption") @Valid ImageText imageCaption
    ) {}
}
