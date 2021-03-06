(ns lobos.migrations
  (:require [ruuvi-server.configuration :as conf])
  (:refer-clojure :exclude [alter drop
                            bigint boolean char double float time complement])
  (:use [lobos.migration :only (defmigration)]
        [lobos.connectivity :only (open-global close-global)]
        [lobos.core :only (create drop alter rollback migrate)]
        [lobos.schema :only (varchar integer timestamp index table decimal)]
        [lobos.helpers :only (table-entity refer-to)]
        [clojure.tools.logging :only (info warn error)])
  )

(defmigration add-trackers-table
  (up []
      (create
              (table-entity :trackers
                            (varchar :tracker_code 256 :not-null :unique)
                            (varchar :name 256)
                            (timestamp :latest_activity)
                            (varchar :shared_secret 64)
                            ))      
      (create (index :trackers :ix_trackers_name [:name]))
     )
  (down [] (drop (table :trackers))))
 
(defmigration add-events-table
  (up []
      (create
       (table-entity :events
                     (refer-to :trackers)
                     (timestamp :event_time :not-null)
                     ))
      (create (index :events :ix_events_event_time [:event_time]))
      (create (index :events :ix_events_created_on [:created_on]))
      (create (index :events :ix_events_trackers_id [:tracker_id]))
      )
  
  (down [] (drop (table :events))))

(defmigration add-event-locations-table
  (up [] (create
          (table-entity :event_locations
                        (refer-to :events)
                        (decimal :latitude :not-null)
                        (decimal :longitude :not-null)
                        (varchar :accuracy 20)
                        (integer :satellite_count)
                        (decimal :altitude))))
  (down [] (drop (table :event_locations))))

(defmigration add-event-annotations-table
  (up [] (create
          (table-entity :event_annotations
                        (refer-to :events)
                        (varchar :annotation 256))))
  (down [] (drop (table :event_annotations))))

(defmigration add-event-extension-types-table
  (up []
      (create
       (table-entity :event_extension_types
                     (varchar :name 256 :not-null)
                     (varchar :description 256)))
      (create (index :event_extension_types [:name]))
      )
  (down [] (drop (table :event_extension_types))))

(defmigration add-event-extension-values-table
  (up [] (create
          (table-entity :event_extension_values
                        (refer-to :events)
                        (refer-to :event_extension_types)
                        (varchar :value 256))))
  (down [] (drop (table :event_extension_values))))

;; reference from events to event_sessions is optional/nullable
(defmigration add-event-sessions-table
  (up []
      (create
       (table-entity :event_sessions
                     (refer-to :trackers)
                     (varchar :session_code 50 :not-null)
                     (timestamp :latest_event_time)
                     (timestamp :first_event_time)))
       (create (index :event_sessions :ix_event_sessions_code
                      [:session_code :tracker_id] :unique))
       (create (index :event_sessions :ix_event_sessions_latest
                      [:latest_event_time]))
       (alter :add (table :events
                          (integer :event_session_id
                                   [:refer :event_sessions :id :on-delete :cascade]
                                   )))
       )
  (down []
        (alter :drop (table :events (integer :event_session_id)))
        (drop (table :event_sessions)))
  )

(defmigration add-event-locations-heading-speed-columns
  (up []
      (alter :add (table :event_locations
                         (decimal :heading 5 2))) ; 3 integers, 2 decimals
      (alter :add (table :event_locations
                         (decimal :speed 5 2))) ; 3 integers, 2 decimals
      )
  (down []
        (alter :drop (table :event_locations (decimal :speed)))
        (alter :drop (table :event_locations (decimal :heading)))
        )
  )

(defmigration add-horizontal-and-vertical-accuracy
  (up []
      (alter :drop (table :event_locations (varchar :accuracy 20)))
      (alter :add (table :event_locations
                         (decimal :horizontal_accuracy 10 2)))
      (alter :add (table :event_locations
                         (decimal :vertical_accuracy 10 2) ))
      )
  (down []
        (alter :drop (table :event_locations (decimal :horizontal_accuracy)))
        (alter :drop (table :event_locations (decimal :vertical_accuracy)))
        (alter :add (table :event_locations (varchar :accuracy 20)))
        )
  )

(defmigration add-tracker-api-password-auth
  (up [] (alter :add (table :trackers (varchar :password 64))))
  (down [] (alter :drop (table :trackers (varchar :password 64))))
  )

(defn do-migration [config direction]
  (info "Execute" (name direction))
  (let [dbh (:database config)
        db (merge dbh {:datasource (get-in config [:database :datasource] config)})
        ]
    (try
      (open-global db) 
      (if (= :rollback direction)
        (do
          (info "rollbacking")
          (rollback :all))
        (do
          (info "migrating forward")
          (migrate))
        )
    (finally (close-global))))
  (println "Done")   
  )