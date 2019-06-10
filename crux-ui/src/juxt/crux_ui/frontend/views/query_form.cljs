(ns juxt.crux-ui.frontend.views.query-form
  (:require [re-frame.core :as rf]
            [garden.core :as garden]
            [juxt.crux-ui.frontend.views.query-editor :as q-editor]))

(def ^:private -sub-query-input-malformed (rf/subscribe [:subs.query/input-malformed?]))

(defn- on-submit [e]
  (rf/dispatch [:evt.ui/query-submit]))

(defn btn []
  {:background "hsl(190, 50%, 65%)"
   :color      "hsl(0, 0%, 100%)"
   :cursor     :pointer
   :border     0
   :padding    "12px 16px"
   :border-radius :2px})

(def q-form-styles
  (garden/css
    [:.q-form
      [:&__submit
       {:padding "8px 0"}]
      [:&__submit-btn
       (btn)]]))

(defn root []
  [:div.q-form
   [:style q-form-styles]
   [:div.q-form__editor
    [q-editor/root]]
   (if-let [e @-sub-query-input-malformed]
     [:div.q-form__editor-err
      "Query input appears to be malformed: " (.-message e)])
   [:div.q-form__submit
     [:button.q-form__submit-btn {:on-click on-submit} "Run Query (ctrl+enter)"]]])

