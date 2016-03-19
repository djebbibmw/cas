package org.jasig.cas.web;

import org.jasig.cas.web.support.CasBanner;
import org.springframework.boot.actuate.autoconfigure.MetricsDropwizardAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jersey.JerseyAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.velocity.VelocityAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;

/**
 * This is {@link CasWebApplication}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@SpringBootApplication(scanBasePackages = {"org.jasig.cas"},
        exclude = {HibernateJpaAutoConfiguration.class,
                JerseyAutoConfiguration.class,
                GroovyTemplateAutoConfiguration.class,
                DataSourceAutoConfiguration.class,
                MetricsDropwizardAutoConfiguration.class,
                VelocityAutoConfiguration.class})
@ImportResource(locations = {"classpath:/spring-configuration/*.xml",
        "classpath:/spring-configuration/*.groovy",
        "classpath:/deployerConfigContext.xml",
        "classpath*:/META-INF/spring/*.xml"})
@Import(value= {AopAutoConfiguration.class})
@EnableConfigServer
public class CasWebApplication {
    /**
     * Instantiates a new Cas web application.
     */
    protected CasWebApplication() {
    }

    /**
     * Main entry point of the CAS web application.
     *
     * @param args the args
     */
    public static void main(final String[] args) {
        new SpringApplicationBuilder(CasWebApplication.class)
                .banner(new CasBanner())
                .run(args);
    }
}
