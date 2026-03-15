import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Article implements Comparable<Article> {
	public String uuid;
	public String title;
	public String author;
	public String url;
	public String text;
	public String published;
	public String language;
	public List<String> categories;

	// folosit pentru debug
	@Override
	public String toString() {
		return "Article [uuid=" + uuid + ", title=" + title + ", author=" + author + ", lang=" + language + " ]";
	}

	// folosit pentru sortarea listei cu articole
	@Override
	public int compareTo(Article article) {
		int c = article.published.compareTo(published);
		if (c != 0)
			return c;
		c = uuid.compareTo(article.uuid);
		if (c != 0)
			return c;
		return 0;
	}
}