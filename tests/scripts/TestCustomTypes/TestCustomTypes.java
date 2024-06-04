import java.net.MalformedURLException;
import java.net.URL;

import customtypes.*;

public class TestCustomTypes {

  public static void main(String[] args) throws MalformedURLException {
    // Get the custom types and check their data
    CustomTypesDemo demo = CustomTypes.getCustomTypesDemo(null);
    // URL is customized on the bindings side
    assert demo.url().equals(new Url(new URL("http://example.com/")));
    // Handle isn't, but because java doesn't have type aliases it's still wrapped.
    assert demo.handle().equals(new Handle(123L));

    // // Change some data and ensure that the round-trip works
    demo.setUrl(new Url(new URL("http://new.example.com/")));
    demo.setHandle(new Handle(456L));
    assert demo.equals(CustomTypes.getCustomTypesDemo(demo));
  }
}
