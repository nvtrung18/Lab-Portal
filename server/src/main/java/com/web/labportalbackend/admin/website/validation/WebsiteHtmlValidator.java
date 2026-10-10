package com.web.labportalbackend.admin.website.validation;

import com.web.labportalbackend.admin.website.dto.WebsiteContent;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

public class WebsiteHtmlValidator implements ConstraintValidator<SafeWebsiteHtml, String> {
    private static final Pattern LINK_URL = Pattern.compile(WebsiteContent.PAGE_LINK_URL);
    private static final Pattern IMAGE_URL = Pattern.compile(WebsiteContent.ASSET_URL);
    private static final Safelist ALLOWED = Safelist.none()
            .addTags("p", "br", "h2", "h3", "strong", "b", "em", "i", "u", "s", "ul", "ol", "li", "blockquote", "a", "img")
            .addAttributes("a", "href", "target", "rel", "title")
            .addAttributes("img", "src", "alt", "title")
            .addAttributes("ol", "start")
            .addProtocols("a", "href", "http", "https", "#")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        if (value.length() > 200000) return false;
        Document document = Jsoup.parseBodyFragment(value, "https://website.invalid/");
        if (!new Cleaner(ALLOWED).isValid(document)) return false;
        return document.select("a[href]").stream().allMatch(link -> LINK_URL.matcher(link.attr("href")).matches())
                && document.select("img").stream().allMatch(image -> !image.attr("src").isEmpty()
                    && IMAGE_URL.matcher(image.attr("src")).matches())
                && document.select("a[target]").stream().allMatch(link -> "_blank".equals(link.attr("target"))
                    || "_self".equals(link.attr("target")));
    }
}
