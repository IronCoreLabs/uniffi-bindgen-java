import java.util.List;
import java.util.Map;

import uniffi.rondpoint.*;

public class TestRondpoint {
  public static void main(String[] args) throws Exception {
    Dictionnaire dico = new Dictionnaire(Enumeration.DEUX, true, (byte)0, 123456789L);
    Dictionnaire copyDico = Rondpoint.copieDictionnaire(dico);
    assert dico.equals(copyDico);
    
    assert Rondpoint.copieEnumeration(Enumeration.DEUX).equals(Enumeration.DEUX);
    assert Rondpoint.copieEnumerations(List.of(Enumeration.UN, Enumeration.DEUX)).equals(List.of(Enumeration.UN, Enumeration.DEUX));
    assert Rondpoint.copieCarte(Map.ofEntries(
      Map.entry("0", new EnumerationAvecDonnees.Zero()),
      Map.entry("1", new EnumerationAvecDonnees.Un(1)),
      Map.entry("2", new EnumerationAvecDonnees.Deux(2, "deux"))
    )).equals(Map.<String, EnumerationAvecDonnees>ofEntries(
      Map.entry("0", new EnumerationAvecDonnees.Zero()),
      Map.entry("1", new EnumerationAvecDonnees.Un(1)),
      Map.entry("2", new EnumerationAvecDonnees.Deux(2, "deux")))
    );
  }
}
