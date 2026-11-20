package com.loleague.meme.media;

import com.loleague.common.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    /** Step 1 of an upload: get a 5-minute presigned S3 URL, then PUT the file to it from the browser. */
    @PostMapping("/presign")
    public MediaService.PresignResponse presign(CurrentUser me, @RequestBody MediaService.PresignRequest req) {
        return media.presign(me, req);
    }
}
