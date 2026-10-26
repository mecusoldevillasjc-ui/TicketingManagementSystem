package ticketing_management_system.util;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Flash-message helpers: the message survives the redirect and appears as a toast. */
public final class Web {
    private Web() {}

    public static void ok(RedirectAttributes ra, String msg) {
        ra.addFlashAttribute("toast", msg);
        ra.addFlashAttribute("toastType", "ok");
    }

    public static void err(RedirectAttributes ra, String msg) {
        ra.addFlashAttribute("toast", msg);
        ra.addFlashAttribute("toastType", "error");
    }

    /** error == null means success. */
    public static void result(RedirectAttributes ra, String error, String success) {
        if (error != null) err(ra, error); else ok(ra, success);
    }
}
