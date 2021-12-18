import React, {useCallback, useState} from "react";

export const NotificationContext = React.createContext({
  notification: null,
  addNotification: () => {},
  removeNotification: () => {}
});

const NotificationContextProvider = ({ children }) => {
  const [notification, setNotification] = useState(null);

  const removeNotification = () => setNotification(null);

  const contextValue = {
    notification,
    addNotification: useCallback((message, type) => {
      setNotification({ message, type });
    }, []),
    removeNotification: useCallback(() => removeNotification(), [])
  };

  return (
      <NotificationContext.Provider value={contextValue}>
        {children}
      </NotificationContext.Provider>
  );

}

export default NotificationContextProvider;