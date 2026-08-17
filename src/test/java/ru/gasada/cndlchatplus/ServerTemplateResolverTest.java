package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ServerTemplateResolverTest {
	private RootConfig root;
	private ServerTemplateResolver resolver;

	@BeforeEach
	void setUp() {
		root = new RootConfig();
		root.templates.add(info("default", List.of()));
		root.templates.add(info("wild", List.of("*.example.org")));
		root.templates.add(info("exact", List.of("play.example.org:25566")));
		root.templates.add(info("specific", List.of("*.sub.example.org")));
		root.templates.add(info("bound", List.of()));
		root.defaultTemplateId = "default";
		resolver = new ServerTemplateResolver();
	}

	@Test
	void explicitBindingWinsOverEveryPattern() {
		assertTrue(resolver.bindExact(root, "PLAY.EXAMPLE.ORG:25566", "bound").success());
		ServerTemplateResolver.Resolution result = resolver.resolve(root, "play.example.org:25566");
		assertEquals("bound", result.templateId());
		assertEquals(ServerTemplateResolver.ResolutionSource.EXPLICIT_BINDING, result.source());
	}

	@Test
	void exactPatternWinsOverWildcard() {
		ServerTemplateResolver.Resolution result = resolver.resolve(root, "PLAY.Example.Org:25566");
		assertEquals("exact", result.templateId());
		assertEquals(ServerTemplateResolver.ResolutionSource.EXACT_PATTERN, result.source());
	}

	@Test
	void mostSpecificConflictingWildcardWins() {
		ServerTemplateResolver.Resolution result = resolver.resolve(root, "node.sub.example.org");
		assertEquals("specific", result.templateId());
		assertEquals(ServerTemplateResolver.ResolutionSource.WILDCARD_PATTERN, result.source());
	}

	@Test
	void wildcardDoesNotMatchBareDomain() {
		assertEquals("default", resolver.resolve(root, "example.org").templateId());
	}

	@Test
	void fallsBackToDefaultThenSafeNone() {
		assertEquals("default", resolver.resolve(root, "unmatched.test").templateId());
		root.defaultTemplateId = null;
		ServerTemplateResolver.Resolution none = resolver.resolve(root, "unmatched.test");
		assertFalse(none.resolved());
		assertEquals(ServerTemplateResolver.ResolutionSource.NONE, none.source());
	}

	@Test
	void normalizesDefaultPortTrailingDotAndInternationalHost() {
		assertEquals("example.org:25565", ServerAddressNormalizer.normalize("Example.Org.").normalizedAddress());
		assertEquals("xn--e1afmkfd.xn--p1ai:25565",
				ServerAddressNormalizer.normalize("пример.рф").normalizedAddress());
	}

	@Test
	void validatesPatternsNamesAndInvalidPorts() {
		assertTrue(AddressPatternValidator.validate("*.Example.org:25565").valid());
		assertFalse(AddressPatternValidator.validate("foo.*.example.org").valid());
		assertFalse(AddressPatternValidator.validate("example.org:70000").valid());
		assertEquals("Шаблон", TemplateNameValidator.validate("  Шаблон  ").normalizedName());
		assertFalse(TemplateNameValidator.validate("\n").valid());
	}

	private static ServerTemplateInfo info(String id, List<String> patterns) {
		ServerTemplateInfo info = new ServerTemplateInfo(id, id);
		info.addressPatterns.addAll(patterns);
		return info;
	}
}
