package at.obyoxar.jwtverify.configuration

import org.springframework.context.annotation.Configuration
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration
import org.springframework.security.oauth2.provider.expression.OAuth2MethodSecurityExpressionHandler

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true ,proxyTargetClass = true)
class OAuth2ResourceServerSecurityConfig: GlobalMethodSecurityConfiguration() {
    override fun createExpressionHandler(): MethodSecurityExpressionHandler {
        return OAuth2MethodSecurityExpressionHandler()
    }
}