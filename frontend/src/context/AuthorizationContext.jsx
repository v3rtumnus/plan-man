import React, { useReducer } from "react";

let user = localStorage.getItem("currentUser")
    ? JSON.parse(localStorage.getItem("currentUser")).user
    : "";
let token = localStorage.getItem("currentUser")
    ? JSON.parse(localStorage.getItem("currentUser")).auth_token
    : "";

const initialState = {
  user: "" || user,
  token: "" || token,
  loading: false,
  errorMessage: null
};

export const actionTypes = {
  REQUEST_LOGIN: "REQUEST_LOGIN",
  LOGIN_SUCCESS: "LOGIN_SUCCESS",
  LOGOUT: "LOGOUT",
  LOGIN_ERROR: "LOGIN_ERROR"
}

// const useActions = (dispatch) => {
//   return {
//     retrieveUnreadMessageCount: async () => {
//       const data = await getUnreadMessagesCountByCustomerId();
//       dispatch({type: actionTypes.FETCH_MESSAGES, payload: data});
//     }
//   }
// }

const reducer = (initialState, action) => {
  switch (action.type) {
    case "REQUEST_LOGIN":
      return {
        ...initialState,
        loading: true
      };
    case "LOGIN_SUCCESS":
      return {
        ...initialState,
        user: action.payload.user,
        token: action.payload.auth_token,
        loading: false
      };
    case "LOGOUT":
      return {
        ...initialState,
        user: "",
        token: ""
      };

    case "LOGIN_ERROR":
      return {
        ...initialState,
        loading: false,
        errorMessage: action.error
      };
  }
}

export const AuthorizationContext = React.createContext(initialState);

const AuthorizationContextProvider = ({ children }) => {
  const [authorizationData, dispatch] = useReducer(reducer, initialState);
  const actions = useActions(dispatch);

}