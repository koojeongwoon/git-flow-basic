package example.existing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
class ExistingRepositoryTest {
 @Test void originalReadmeIsPreserved() throws Exception { assertEquals(7454L, Files.size(Path.of("README.md"))); }
}
