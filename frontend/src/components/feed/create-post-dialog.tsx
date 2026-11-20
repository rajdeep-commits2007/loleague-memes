"use client";

import { useRef, useState } from "react";
import { ImagePlus, Loader2, Send, X } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { useCreatePostMutation, usePresignMutation } from "@/store/api/memeApi";
import { ACCEPTED_IMAGE_TYPES, putToS3, validateImage } from "@/lib/upload";
import { errorMessage } from "@/lib/errors";

const MAX_MB = 8;

export function CreatePostDialog({ trigger }: { trigger: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [caption, setCaption] = useState("");
  const [progress, setProgress] = useState<number | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [presign] = usePresignMutation();
  const [createPost] = useCreatePostMutation();
  const busy = progress !== null;

  function reset() {
    if (preview) URL.revokeObjectURL(preview);
    setFile(null);
    setPreview(null);
    setCaption("");
    setProgress(null);
  }

  function pick(f: File | undefined) {
    if (!f) return;
    const problem = validateImage(f, MAX_MB);
    if (problem) {
      toast.error(problem);
      return;
    }
    if (preview) URL.revokeObjectURL(preview);
    setFile(f);
    setPreview(URL.createObjectURL(f));
  }

  async function submit() {
    if (!file) return;
    try {
      setProgress(0);
      const signed = await presign({ purpose: "MEME", contentType: file.type, contentLength: file.size }).unwrap();
      await putToS3(signed, file, setProgress);
      await createPost({ imageKey: signed.key, caption: caption.trim() || undefined }).unwrap();
      toast.success("Meme posted! Now go farm those comments 🔥");
      reset();
      setOpen(false);
    } catch (e) {
      setProgress(null);
      toast.error(e instanceof Error && !("status" in e) ? e.message : errorMessage(e));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (busy) return;
        setOpen(o);
        if (!o) reset();
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Drop a meme</DialogTitle>
          <DialogDescription>Only students from your college will see it. Keep it funny, not cruel.</DialogDescription>
        </DialogHeader>

        {preview ? (
          <div className="relative overflow-hidden rounded-xl border-2 border-border bg-grape-soft">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={preview} alt="Preview" className="max-h-80 w-full object-contain" />
            {!busy && (
              <Button
                size="icon-sm"
                variant="outline"
                className="absolute top-2 right-2"
                onClick={() => {
                  if (preview) URL.revokeObjectURL(preview);
                  setFile(null);
                  setPreview(null);
                }}
                aria-label="Remove image"
              >
                <X />
              </Button>
            )}
          </div>
        ) : (
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              pick(e.dataTransfer.files?.[0]);
            }}
            className="flex h-52 flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-grape bg-grape-soft transition hover:bg-accent"
          >
            <ImagePlus className="size-10 text-bubblegum" />
            <span className="font-display font-bold">Tap to pick an image</span>
            <span className="text-xs text-muted-foreground">JPG, PNG, WEBP or GIF · up to {MAX_MB} MB</span>
          </button>
        )}
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPTED_IMAGE_TYPES.join(",")}
          className="hidden"
          onChange={(e) => {
            pick(e.target.files?.[0]);
            e.target.value = "";
          }}
        />

        <Textarea
          value={caption}
          onChange={(e) => setCaption(e.target.value.slice(0, 500))}
          placeholder="Caption it (optional)... #8amclass"
          disabled={busy}
        />
        <p className="-mt-2 text-right text-xs text-muted-foreground">{caption.length}/500</p>

        {busy && (
          <div className="h-3 overflow-hidden rounded-full border-2 border-ink bg-card">
            <div className="h-full bg-grape transition-all" style={{ width: `${progress}%` }} />
          </div>
        )}

        <DialogFooter>
          <Button onClick={submit} disabled={!file || busy} size="lg">
            {busy ? <Loader2 className="animate-spin" /> : <Send />}
            {busy ? (progress! < 100 ? `Uploading ${progress}%` : "Posting...") : "Post meme"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
