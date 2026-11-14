package kr.itsdev.devjobcollector.collection.adapter;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/** Accept only direct HTTPS raster images hosted by the verified board origin. */
@Component
public class RecruitBoardDetailParser {
    static final int MAX_IMAGES = 10;

    public List<String> images(String html, URI detail) {
        var doc = Jsoup.parse(html);
        var containers = doc.select(".board-content");
        if (containers.size() != 1) throw new IllegalArgumentException("Unrecognized detail page");
        var images = new LinkedHashSet<String>();
        for (var element : containers.first().select("img[src]")) {
            URI image;
            try { image = detail.resolve(element.attr("src").trim()); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid image URL", ex); }
            String path = image.getRawPath();
            if (!"https".equalsIgnoreCase(image.getScheme()) || image.getHost() == null
                    || !image.getHost().equalsIgnoreCase(detail.getHost()) || image.getRawUserInfo() != null
                    || (image.getPort() != -1 && image.getPort() != 443) || image.getRawFragment() != null
                    || image.toString().length() > 1000 || path == null
                    || !path.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp)$"))
                throw new IllegalArgumentException("Untrusted image URL");
            images.add(image.toString());
            if (images.size() > MAX_IMAGES) throw new IllegalArgumentException("Too many images");
        }
        return List.copyOf(images);
    }
}
