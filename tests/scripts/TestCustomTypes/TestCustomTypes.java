import java.net.MalformedURLException;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;

import customtypes.*;

public class TestCustomTypes {

  public static void main(String[] args) throws MalformedURLException, URISyntaxException {
    // Get the custom types and check their data
    CustomTypesDemo demo = CustomTypes.getCustomTypesDemo(null);
    // URL is customized on the bindings side
    assert demo.url().equals(new Url(new URI("http://example.com/").toURL()));
    // Handle isn't, but because java doesn't have type aliases it's still wrapped.
    assert demo.handle().equals(new Handle(123L));

    // // Change some data and ensure that the round-trip works
    demo.setUrl(new Url(new URI("http://new.example.com/").toURL()));
    demo.setHandle(new Handle(456L));
    assert demo.equals(CustomTypes.getCustomTypesDemo(demo));
  }
}
