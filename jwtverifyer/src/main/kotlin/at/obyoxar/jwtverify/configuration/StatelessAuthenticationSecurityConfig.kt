package at.obyoxar.jwtverify.configuration

import at.obyoxar.jwtverify.lib.OJwtDecoderBase
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.PasswordLookup
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.*
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.ObjectPostProcessor
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.provider.token.store.KeyStoreKeyFactory
import org.springframework.security.web.access.channel.ChannelProcessingFilter
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter
import org.springframework.social.UserIdSource
import org.springframework.social.security.SocialAuthenticationFilter
import org.springframework.social.security.SpringSocialConfigurer
import java.security.Key
import java.security.KeyStore

@Configuration
@Order(2)
@EnableGlobalMethodSecurity(prePostEnabled = true)
class StatelessAuthenticationSecurityConfig: WebSecurityConfigurerAdapter() {

    @Autowired
    lateinit var passwordEncoder: BCryptPasswordEncoder

    @Autowired
    lateinit var authenticationSuccessHandler: SocialAuthenticationSuccessHandler

    @Autowired
    lateinit var statelessAuthenticationFilter: StatelessAuthenticationFilter

    @Autowired
    lateinit var userIdSource: UserIdSource

    @Autowired
    lateinit var userService: UserService

    override fun configure(http: HttpSecurity?) {
        val socialConfigurer = SpringSocialConfigurer()
        socialConfigurer.addObjectPostProcessor(object: ObjectPostProcessor<SocialAuthenticationFilter> {
            override fun <O : SocialAuthenticationFilter?> postProcess(socialAuthenticationFilter: O): O {
                socialAuthenticationFilter!!.setAuthenticationSuccessHandler(authenticationSuccessHandler)
                return socialAuthenticationFilter
            }
        })

        http!!
                .exceptionHandling().and().anonymous().and().servletApi().and().headers().cacheControl().and().and()
                .authorizeRequests()
                .antMatchers("/login").permitAll()
                .antMatchers("/verify").permitAll()
                .antMatchers("/oauth/token/revokeById/**").permitAll()
                .antMatchers("/**/favicon.ico").permitAll()
                .antMatchers("/tokens/**").permitAll()

                //Copied from GitHub
                .antMatchers("/").permitAll()
                .antMatchers("/auth/**").permitAll()
                .antMatchers("/resources/**").permitAll()
                .antMatchers(HttpMethod.GET, "/api/**").permitAll()
                .antMatchers("/admin/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.GET, "/api/users/current/details").hasAuthority("USER")
                .antMatchers(HttpMethod.GET, "/user").hasAuthority("USER")
                .antMatchers(HttpMethod.GET, "/social").hasAuthority("USER")
                .antMatchers(HttpMethod.GET, "/auth/info/me").hasAuthority("USER")
                .and()
                .addFilter(JwtAuthenticationFilter(authenticationManager()))
                .addFilter(JwtAuthorizationFilter(authenticationManager(), userService))
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .addFilterBefore(statelessAuthenticationFilter, AbstractPreAuthenticatedProcessingFilter::class.java)


//                .addFilterAfter({req, resp, chain ->
//                    val auth = SecurityContextHolder.getContext().authentication
//                    chain.doFilter(req, resp)
//                }, FilterSecurityInterceptor::class.java)
//                .addFilterBefore({req, resp, chain ->
//                    val auth = SecurityContextHolder.getContext().authentication
//                    chain.doFilter(req, resp)
//                }, ChannelProcessingFilter::class.java)

                .apply(socialConfigurer.userIdSource(userIdSource))
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val res = javaClass.getResource("/keys/obykeys.jks")
        val keyStore = KeyStore.getInstance("JKS")
        keyStore.load(res.openStream(), "obypass".toCharArray())
        val pwLookup = PasswordLookup { "obypass".toCharArray() }
        val jwkLoadedSet = JWKSet.load(keyStore, null)
        val jwkSet = ImmutableJWKSet<SecurityContext>(jwkLoadedSet)

        val rsakey = RSAKey.load(keyStore, "obykey", "obypass".toCharArray())


        val keyStoreFactory = KeyStoreKeyFactory(ClassPathResource("keys/obykeys.jks"), "obypass".toCharArray())
        val keyPair = keyStoreFactory.getKeyPair("obykey")

        val selector = JWSKeySelector<SecurityContext> { header, context -> mutableListOf(keyPair.public as Key) }
        val source = JWKSource<SecurityContext> { jwkSelector, _ -> mutableListOf(rsakey as JWK) }
        return OJwtDecoderBase(source, "RS256")
    }


    @Bean
    override fun authenticationManagerBean(): AuthenticationManager {
        return super.authenticationManagerBean()
    }

    override fun configure(auth: AuthenticationManagerBuilder) {
        auth.userDetailsService(userService)
    }

    override fun userDetailsService(): SocialUserService {
        return userService
    }
}

