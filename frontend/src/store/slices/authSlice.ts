import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { User } from "@/lib/types";

type Status = "unknown" | "authenticated" | "anonymous";

interface AuthState {
  user: User | null;
  status: Status;
}

const initialState: AuthState = { user: null, status: "unknown" };

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    loggedIn(state, action: PayloadAction<User>) {
      state.user = action.payload;
      state.status = "authenticated";
    },
    userUpdated(state, action: PayloadAction<User>) {
      state.user = action.payload;
    },
    loggedOut(state) {
      state.user = null;
      state.status = "anonymous";
    },
    sessionExpired(state) {
      state.user = null;
      state.status = "anonymous";
    },
  },
});

export const { loggedIn, userUpdated, loggedOut, sessionExpired } = authSlice.actions;
export default authSlice.reducer;
