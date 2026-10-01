package com.example.skeleton.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트로 강제되는 아키텍처 규약. 규칙이 실패하면 코드를 고칠 것.
 * docs/adr에 결정을 기록하지 않고 규칙을 완화하지 않음.
 */
@AnalyzeClasses(packages = ArchitectureTest.BASE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE = "com.example.skeleton";

    // 레이어 패턴은 베이스 패키지에 앵커함.
    // "..api.." 같은 비앵커 패턴은 베이스 패키지 자체에 api·domain 같은 세그먼트가 있으면(com.acme.shop.api) 모든 클래스를 그 레이어로 오판함.
    private static final String API = BASE + ".*.api..";
    private static final String APPLICATION = BASE + ".*.application..";
    private static final String DOMAIN = BASE + ".*.domain..";
    private static final String STORAGE = BASE + ".*.storage..";
    private static final String COMMON = BASE + ".common..";

    @ArchTest
    static final ArchRule packages_are_free_of_cycles = slices().matching(BASE + ".(*)..").should()
            .beFreeOfCycles();

    @ArchTest
    static final ArchRule feature_packages_are_independent = slices().matching(BASE + ".(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(alwaysTrue(), resideInAPackage(COMMON));

    @ArchTest
    static final ArchRule common_must_not_depend_on_features = noClasses()
            .that()
            .resideInAPackage(COMMON)
            .should()
            .dependOnClassesThat(resideInAPackage(BASE + "..")
                    .and(resideInAPackage(COMMON).negate()));

    @ArchTest
    static final ArchRule controllers_live_in_feature_packages = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .haveSimpleNameEndingWith("Controller")
            .andShould()
            .resideOutsideOfPackage(COMMON);

    @ArchTest
    static final ArchRule controllers_must_not_touch_repositories = noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule no_field_injection = noFields().should().beAnnotatedWith(Autowired.class);

    // 기능 내부 레이어 의존 방향은 api에서 application, application에서 domain이며 storage도 domain에 의존함(ADR 0008).

    @ArchTest
    static final ArchRule domain_is_the_dependency_free_core = noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(API, APPLICATION, STORAGE);

    @ArchTest
    static final ArchRule application_must_not_depend_on_web_or_storage = noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(API, STORAGE);

    @ArchTest
    static final ArchRule storage_only_implements_domain = noClasses()
            .that()
            .resideInAPackage(STORAGE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(API, APPLICATION);
}
