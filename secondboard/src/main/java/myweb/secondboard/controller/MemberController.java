package myweb.secondboard.controller;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import myweb.secondboard.domain.Member;
import myweb.secondboard.domain.MemberUploadFile;
import myweb.secondboard.domain.Player;
import myweb.secondboard.dto.FindPasswordForm;
import myweb.secondboard.dto.MemberSaveForm;
import myweb.secondboard.dto.MemberUpdateForm;
import myweb.secondboard.dto.UpdatePasswordForm;
import myweb.secondboard.service.MatchingService;
import myweb.secondboard.service.MemberImageService;
import myweb.secondboard.service.MemberService;
import myweb.secondboard.service.PlayerService;
import org.json.simple.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Controller
@RequestMapping("members")
@RequiredArgsConstructor
public class MemberController {

  private final MemberService memberService;

  private final PlayerService playerService;
  private final MatchingService matchingService;
  private final MemberImageService memberImageService;

  @GetMapping("/new")
  public String signUpPage(Model model) {
    model.addAttribute("member", new MemberSaveForm());
    return "members/signUpPage";
  }

  @PostMapping("/new")
  public String signUp(@Validated @ModelAttribute("member") MemberSaveForm form,
                       BindingResult bindingResult) throws NoSuchAlgorithmException {
    if (bindingResult.hasErrors()) {
      log.info("errors = {}", bindingResult);
      return "members/signUpPage";
    }
    //SignUp Success Logic
    Long memberId = memberService.signUp(form);


    return "redirect:/";
  }

  // ?????????
  @GetMapping("/profile/{memberId}")
  public String profileHome(@PathVariable("memberId") Long memberId, Model model) {

    Member member = memberService.findById(memberId);
    model.addAttribute("member", member);

    // ????????? ?????? ?????????
    MemberUpdateForm form = new MemberUpdateForm();
    form.setId(member.getId());
    form.setNickname(member.getNickname());
    form.setIntroduction(member.getIntroduction());
    model.addAttribute("form",form);

    List<Player> playerList = playerService.findByMemberId(member.getId());
    List<Player> playerAfterList = playerService.findByAfterMemberId(member.getId());
    List<Player> playerBeforeList = playerService.findByBeforeMemberId(member.getId());
    model.addAttribute("playerList", playerList);
    model.addAttribute("afterPlayerList", playerAfterList);
    model.addAttribute("playerBeforeList", playerBeforeList);

//    System.out.println("playerList = " + playerList);

    return "members/profileHome";
  }

  @PostMapping("/profileUpdate")
  public String profileUpdate(@ModelAttribute("form")MemberUpdateForm form,
                              BindingResult bindingResult, Model model, MultipartFile file) throws IOException {

    try {
      if (file != null) {
        MemberUploadFile uploadFile = memberImageService.updateStore(file);
        memberService.setProfileImage("/image/member/"+uploadFile.getId(), form);
      }
    } catch(Exception e) {
      e.printStackTrace();
    }

    Long memberId = memberService.updateMember(form, file);
    model.addAttribute("introductionLength", form.getIntroduction().length());
    return "redirect:/members/profile/"+memberId;
  }

  //==???????????? ??????==//
  @GetMapping("/find/password")
  public String findPassword(Model model) {
    model.addAttribute("form", new FindPasswordForm());
    return "members/findPassword";
  }

  //==???????????? ?????? : ????????? ?????? ??????==//
  @PostMapping("/find")
  public String findPassword(@Valid @ModelAttribute("form") FindPasswordForm form, BindingResult bindingResult) {

    System.out.println("form.getLoginId() = " + form.getLoginId());
    Optional<Member> findMemberByLoginId = memberService.findByLoginId(form.getLoginId());
    Long updateMemberLoginId;
    if (findMemberByLoginId.isPresent()) {
      updateMemberLoginId = findMemberByLoginId.get().getId();
    } else {
      bindingResult.reject("NotFoundLoginMember", "???????????? ???????????? ?????? ??? ????????????.");
      return  "members/findPassword";
    }

    return "redirect:/members/find/"+ updateMemberLoginId;
  }

  @GetMapping("/find/{updateMemberLoginId}")
  public String AuthenticateMember(Model model, @ModelAttribute("form") FindPasswordForm form, BindingResult bindingResult,
                                   @PathVariable("updateMemberLoginId") Long updateMemberLoginId) {
    Member updateMember = memberService.findById(updateMemberLoginId);
    String phoneNumber = updateMember.getPhoneNumber();

    //01012341234 -> *1*1*
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < phoneNumber.length(); i++) {
      if (i % 3 == 0) {
        builder.append(phoneNumber.charAt(i));
      }
      else {
        builder.append('*');
      }
    }
    model.addAttribute("loginId",updateMember.getLoginId());
    model.addAttribute("phoneNum",builder.toString());
    model.addAttribute("updateMemberLoginId", updateMemberLoginId);
    return "members/memberAuthentication";
  }

  @GetMapping("/update/password/{updateMemberLoginId}")
  public String updatePassword(Model model,@PathVariable("updateMemberLoginId") Long updateMemberLoginId ) {
    UpdatePasswordForm form = new UpdatePasswordForm();
    form.setId(updateMemberLoginId); // 155
    System.out.println("form.getId() = " + form.getId());
    model.addAttribute("form", form);

    return "members/updateMemberPassword";
  }

  @PostMapping("/update")
  public String updatePassword(@Valid @ModelAttribute("form") UpdatePasswordForm form, BindingResult bindingResult)
          throws NoSuchAlgorithmException {
    //==???????????? ???????????? ??????==//

    Member member = memberService.findById(form.getId()); //????????? ??????
    String updatePassword = form.getUpdatePassword();
    String updatePasswordCheck = form.getUpdatePasswordCheck();

    if (bindingResult.hasErrors()) {
      log.info("errors = {}", bindingResult);
      return "members/updateMemberPassword";
    }

    System.out.println("updatePasswordCheck = " + updatePassword);
    System.out.println("updatePasswordCheck = " + updatePasswordCheck);

    if (!updatePassword.equals(updatePasswordCheck)) {
      bindingResult.reject("NotMatchPassword", "??? ??????????????? ???????????? ????????? ???????????? ????????????.");
      return "members/updateMemberPassword";
    }

    memberService.updatePassword(form, member);

    return "redirect:/";
  }

  //== ?????? ?????? ==//
  @ResponseBody
  @PostMapping("/withdrawl/{memberId}")
  public JSONObject memberWithdrawl(@PathVariable("memberId")Long memberId,
                                HttpServletRequest request, Model model) throws IOException {

    JSONObject result = new JSONObject();

    model.addAttribute("memberId", memberId);

    List<Player> players = playerService.findAll();
    for (Player player : players) {
      if (Objects.equals(player.getMember().getId(), memberId)) { // ????????? ????????? ?????? ?????? ??????

        String matchingStartTime = player.getMatching().getMatchingStartTime(); // 21:30
        LocalDate date = player.getMatching().getMatchingDate(); // 11-18
        LocalDateTime localDateTime = date.atTime(LocalTime.parse(matchingStartTime)); // 11-18 21:30

        if (localDateTime.isAfter(LocalDateTime.now())) { // ?????? ?????? // 11-18-21:30 is After 11-18 5:45

          result.put("result", "error");
          return result;
        }
      }
    }

    result.put("result", "success");

    String uuid = UUID.randomUUID().toString();

    // ?????? ?????? ??????
    memberService.memberWithDrawl(memberId, uuid);

    // ?????? ?????????(????????????)
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }

    return result;
  }


}
