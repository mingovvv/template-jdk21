import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 템플릿의 자리표시자 이름을 새 프로젝트 이름으로 치환하는 일회용 스크립트.
 *
 * <pre>
 * java scripts/Rename.java --name shop-api --package com.acme.shop
 * </pre>
 *
 * 프로젝트 루트에서 실행한다. 치환이 끝나면 이 스크립트는 스스로 삭제된다.
 */
public class Rename {

    // 템플릿 토큰 — 코드 어디에도 우연히 등장하지 않도록 고른 이름이다. 바꾸면 이 목록도 함께 바꿔라.
    static final String TEMPLATE_PACKAGE = "com.example.skeleton";
    static final String TEMPLATE_GROUP_LINE = "group = 'com.example'";
    static final String TEMPLATE_CLASS_PREFIX = "SkeletonApplication";
    static final String TEMPLATE_NAME = "skeleton";

    static final Pattern NAME_FORMAT = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    static final Pattern PACKAGE_FORMAT = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");

    static final Set<String> SKIPPED_DIRS = Set.of(".git", ".gradle", ".idea", "build", "out", "logs", "node_modules");
    static final Set<String> TEXT_EXTENSIONS = Set.of("java", "gradle", "yml", "yaml", "xml", "properties", "md", "sql",
            "json", "txt", "ps1", "sh");

    public static void main(String[] args) throws IOException {
        Map<String, String> options = parseOptions(args);
        String name = options.get("--name");
        String pkg = options.get("--package");
        if (name == null || pkg == null) {
            fail("usage: java scripts/Rename.java --name <kebab-case> --package <java.package>");
        }
        if (!NAME_FORMAT.matcher(name).matches()) {
            fail("--name must be lowercase kebab-case (e.g. shop-api): " + name);
        }
        if (!PACKAGE_FORMAT.matcher(pkg).matches()) {
            fail("--package must be a lowercase Java package (e.g. com.acme.shop): " + pkg);
        }

        Path root = Path.of("").toAbsolutePath();
        Path templateMain = root.resolve("src/main/java").resolve(TEMPLATE_PACKAGE.replace('.', '/'));
        if (!Files.isDirectory(templateMain)) {
            fail("template package not found at " + templateMain
                    + " — run from the project root of an unrenamed template");
        }

        String group = pkg.contains(".") ? pkg.substring(0, pkg.lastIndexOf('.')) : pkg;
        String className = pascalCase(name) + "Application";

        // 긴 토큰부터 한 번의 패스로 치환한다 — 새 이름에 템플릿 토큰이 포함돼도 이중 치환되지 않는다.
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put(TEMPLATE_GROUP_LINE, "group = '" + group + "'");
        replacements.put(TEMPLATE_PACKAGE, pkg);
        replacements.put(TEMPLATE_PACKAGE.replace('.', '/'), pkg.replace('.', '/'));
        replacements.put(TEMPLATE_CLASS_PREFIX, className);
        replacements.put(TEMPLATE_NAME, name);
        Pattern tokens = Pattern.compile(
                replacements.keySet().stream().map(Pattern::quote).collect(Collectors.joining("|")));

        Path self = root.resolve("scripts/Rename.java");
        int changed = 0;
        for (Path file : textFiles(root)) {
            if (file.equals(self)) {
                continue;
            }
            String before = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = tokens.matcher(before);
            String after = matcher.replaceAll(m -> Matcher.quoteReplacement(replacements.get(m.group())));
            if (!after.equals(before)) {
                Files.writeString(file, after, StandardCharsets.UTF_8);
                changed++;
            }
        }

        for (String sourceSet : List.of("main", "test")) {
            Path javaRoot = root.resolve("src").resolve(sourceSet).resolve("java");
            Path from = javaRoot.resolve(TEMPLATE_PACKAGE.replace('.', '/'));
            if (Files.isDirectory(from)) {
                Path to = javaRoot.resolve(pkg.replace('.', '/'));
                movePackage(from, to);
                deleteEmptyParents(from.getParent(), javaRoot);
                renameClassFiles(to, className);
            }
        }

        Files.delete(self);
        deleteEmptyParents(self.getParent(), root);

        System.out.printf("renamed: name=%s package=%s group=%s mainClass=%s filesChanged=%d%n", name, pkg, group,
                className, changed);
        System.out.println("next: ./gradlew clean check   (IntelliJ: reload the Gradle project)");
    }

    static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                fail("unexpected argument: " + args[i]);
            }
            options.put(args[i], args[++i]);
        }
        return options;
    }

    static List<Path> textFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(Rename::isText)
                    .filter(p -> !isUnderSkippedDir(root, p))
                    .forEach(files::add);
        }
        return files;
    }

    static boolean isUnderSkippedDir(Path root, Path file) {
        for (Path part : root.relativize(file)) {
            if (SKIPPED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    static boolean isText(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && TEXT_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    static void movePackage(Path from, Path to) throws IOException {
        if (from.equals(to)) {
            return;
        }
        if (Files.exists(to)) {
            fail("target package directory already exists: " + to);
        }
        // 새 패키지가 기존 패키지의 하위일 수 있으므로(com.example.skeleton.api 등) 임시 위치를 경유한다.
        Path temp = from.resolveSibling(from.getFileName() + ".rename-tmp");
        Files.move(from, temp);
        Files.createDirectories(to.getParent());
        Files.move(temp, to);
    }

    static void renameClassFiles(Path packageDir, String className) throws IOException {
        try (Stream<Path> files = Files.list(packageDir)) {
            for (Path file : files.toList()) {
                String fileName = file.getFileName().toString();
                if (fileName.startsWith(TEMPLATE_CLASS_PREFIX)) {
                    Files.move(file, file.resolveSibling(className + fileName.substring(TEMPLATE_CLASS_PREFIX.length())));
                }
            }
        }
    }

    static void deleteEmptyParents(Path dir, Path stopAt) throws IOException {
        while (dir != null && !dir.equals(stopAt) && Files.isDirectory(dir)) {
            try (Stream<Path> entries = Files.list(dir)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            }
            Files.delete(dir);
            dir = dir.getParent();
        }
    }

    static String pascalCase(String kebab) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebab.split("-")) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    static void fail(String message) {
        System.err.println("error: " + message);
        System.exit(1);
    }
}
