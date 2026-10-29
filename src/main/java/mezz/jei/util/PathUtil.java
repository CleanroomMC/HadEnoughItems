package mezz.jei.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

import org.apache.commons.io.IOUtils;

public final class PathUtil {
	private static boolean atomicMoveSupported = true;

	private PathUtil() {
	}

	/**
	 * Writes with the platform charset and {@code \n} endings, matching how the file is read back.
	 */
	public static Path writeUsingTempFile(Path path, Collection<? extends CharSequence> lines) throws IOException {
		Path tempFile = Files.createTempFile(path.toAbsolutePath().getParent(), null, null);
		try {
			try (Writer writer = Files.newBufferedWriter(tempFile, Charset.defaultCharset())) {
				IOUtils.writeLines(lines, "\n", writer);
			}
			return moveAtomicReplace(tempFile, path);
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	private static Path moveAtomicReplace(Path source, Path target) throws IOException {
		if (atomicMoveSupported) {
			try {
				return Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				atomicMoveSupported = false;
			}
		}
		return Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
	}
}
