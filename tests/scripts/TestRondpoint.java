import java.util.List;
import java.util.Map;

import uniffi.rondpoint.*;

public class TestRondpoint {
  public static void main(String[] args) throws Exception {
    Dictionarre dico = new Dictionnaire(Enumeration.DEUX, true, 0, 123456789);
    Dictionarre copyDico = Rondpoint.copyDico(dico);
    assert dico == copyDico;
    
    assert Rondpoint.copieEnumeration(Enumeration.DEUX) == Enumeration.DEUX;
    assert Rondpoint.copieEnumeration(List.of(Enumeration.UN, Enumeration.DEUX) == List.of(Enumeration.UN, Enumeration.DEUX));
    assert Rondpoint.copieCarte(Map.ofEntries(
      Map.entry("0", EnumerationAvecDonnees.Zero),
      Map.entry("1", EnumerationAvecDonnees.Un(1)),
      Map.entry("2", EnumerationAvecDonnees.Deux(2, "deux"))
    )) == Map.ofEntries(
      Map.entry("0", EnumerationAvecDonnees.Zero),
      Map.entry("1", EnumerationAvecDonnees.Un(1)),
      Map.entry("2", EnumerationAvecDonnees.Deux(2, "deux"))
    );
  }
}
