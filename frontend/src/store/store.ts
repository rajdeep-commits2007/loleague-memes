import { configureStore } from "@reduxjs/toolkit";
import { setupListeners } from "@reduxjs/toolkit/query";
import { baseApi } from "@/store/api/baseApi";
import authReducer from "@/store/slices/authSlice";
import chatReducer from "@/store/slices/chatSlice";

/** A new store per request on the server, one per tab in the browser (Next.js App Router pattern). */
export const makeStore = () => {
  const store = configureStore({
    reducer: {
      auth: authReducer,
      chat: chatReducer,
      [baseApi.reducerPath]: baseApi.reducer,
    },
    middleware: (getDefault) => getDefault().concat(baseApi.middleware),
  });
  setupListeners(store.dispatch);
  return store;
};

export type AppStore = ReturnType<typeof makeStore>;
export type RootState = ReturnType<AppStore["getState"]>;
export type AppDispatch = AppStore["dispatch"];
