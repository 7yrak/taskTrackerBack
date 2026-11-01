package com.tasktracker.security;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
class SharedPortalFilterTest {
 private final String key="01234567890123456789012345678901234567890123456789";
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void acceptsOnlyAuthorizedPortalIdentity()throws Exception{
  var req=new MockHttpServletRequest();var res=new MockHttpServletResponse();req.addHeader("X-Shared-User","tyrak");req.addHeader("X-Shared-Key",key);
  new SharedPortalFilter(key,()->User.withUsername("existing-owner").password("unused").roles("OWNER").build()).doFilter(req,res,(a,b)->assertEquals("existing-owner",SecurityContextHolder.getContext().getAuthentication().getName()));
  assertEquals(200,res.getStatus());assertEquals("tyrak",req.getAttribute("shared.portal.username"));
 }
 @Test void rejectsSpoofedIdentityAndMissingOrWrongKeys()throws Exception{
  for(String value:new String[]{"", "wrong",key}){
   var req=new MockHttpServletRequest();var res=new MockHttpServletResponse();req.addHeader("X-Shared-User",value.equals(key)?"intruder":"tyrak");req.addHeader("X-Shared-Key",value);
   new SharedPortalFilter(key,()->{throw new AssertionError("Owner lookup must not run");}).doFilter(req,res,(a,b)->fail("Must not reach application"));assertEquals(401,res.getStatus());
  }
 }
 @Test void disabledAdapterRejectsIdentityHeaders()throws Exception{
  var req=new MockHttpServletRequest();var res=new MockHttpServletResponse();req.addHeader("X-Shared-User","tyrak");req.addHeader("X-Shared-Key",key);
  new SharedPortalFilter("",()->{throw new AssertionError();}).doFilter(req,res,(a,b)->fail());assertEquals(401,res.getStatus());
 }
 @Test void existingLocalFlowUnchangedWhenNoHeaders()throws Exception{
  new SharedPortalFilter("",()->{throw new AssertionError();}).doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),(a,b)->assertNull(SecurityContextHolder.getContext().getAuthentication()));
 }
}
