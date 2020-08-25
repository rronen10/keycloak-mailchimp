package com.bayamsell.keycloak;

import com.github.alexanderwe.bananaj.connection.MailChimpConnection;
import com.github.alexanderwe.bananaj.model.list.MailChimpList;
import com.github.alexanderwe.bananaj.model.list.member.EmailType;
import com.github.alexanderwe.bananaj.model.list.member.Member;
import com.github.alexanderwe.bananaj.model.list.member.MemberStatus;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MailChimpListenerProvider implements EventListenerProvider {

    private final MailChimpConnection con;
    private final String listId;
    private final Collection<EventType> listenedEvents;
    private final KeycloakSession keycloakSession;

    public MailChimpListenerProvider(String apiKey,
                                     String listId,
                                     Collection<EventType> listenedEvents,
                                     KeycloakSession keycloakSession) {
        this.con = new MailChimpConnection(apiKey);
        this.listenedEvents = listenedEvents;
        this.listId = listId;
        this.keycloakSession = keycloakSession;
    }

    @Override
    public void onEvent(Event event) {
        for (EventType listenedEvent : listenedEvents) {
            if (event.getType().equals(listenedEvent)) {
                KeycloakContext context = keycloakSession.getContext();
                AuthenticationSessionModel authenticationSession = context.getAuthenticationSession();
                UserModel authenticatedUser = authenticationSession.getAuthenticatedUser();
                try {
                    registerUser(authenticatedUser, event.getIpAddress());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void registerUser(UserModel userModel, String ipAddress) throws Exception {
        MailChimpList mainList = con.getList(listId);

        List<String> phone = userModel.getAttribute("phoneNumber");
        String phoneNumber = phone != null && phone.size() > 0 ? phone.get(0) : null;

        List<String> locale = userModel.getAttribute("locale");
        String language = locale != null && locale.size() > 0 ? locale.get(0) : null;

        List<String> photoUrl = userModel.getAttribute("photoUrl");
        String avatarUrl = photoUrl != null && photoUrl.size() > 0 ? photoUrl.get(0) : null;

        List<String> birthdayList = userModel.getAttribute("birthday");
        String birthday = birthdayList != null && birthdayList.size() > 0 ? birthdayList.get(0) : null;

        Map<String, Object> mergeFields = new HashMap<String, Object>();
        mergeFields.put("FNAME", userModel.getFirstName());
        mergeFields.put("LNAME", userModel.getLastName());
        mergeFields.put("PHONE", phoneNumber);
        mergeFields.put("LANGUAGE", language);
        mergeFields.put("AVATAR", avatarUrl);

        // The Date is in the format https://mailchimp.com/fr/help/change-a-date-field-to-a-birthday-field/
        mergeFields.put("BIRTHDAY", birthday(birthday));

        LocalDateTime timeStamp = LocalDateTime.now();
        HashMap<String, Boolean> memberInterest = new HashMap<>();

        Member member = new Member.Builder()
                .emailAddress(userModel.getEmail())
                .language(language)
                .list(mainList)
                .emailType(EmailType.HTML)
                .status(MemberStatus.SUBSCRIBED)
                .mergeFields(mergeFields)
                .statusIfNew(MemberStatus.SUBSCRIBED)
                .ipSignup(ipAddress)
                .timestampSignup(timeStamp)
                .ipOpt(ipAddress)
                .timestampOpt(timeStamp)
                .memberInterest(memberInterest)
                .build();

        mainList.addOrUpdateMember(member);
    }

    private String birthday(String birthday) throws ParseException {
        if (birthday == null) return null;
        Date date = new SimpleDateFormat("dd/MM/yyyy").parse(birthday);
        if (date == null) return null;
        return String.format("%d/%d", date.getMonth() + 1, date.getDate());
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean b) {
    }

    @Override
    public void close() {
    }
}
